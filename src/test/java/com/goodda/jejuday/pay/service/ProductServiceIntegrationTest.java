package com.goodda.jejuday.pay.service;

import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.common.exception.InsufficientHallabongException;
import com.goodda.jejuday.common.exception.OutOfStockException;
import com.goodda.jejuday.pay.entity.Product;
import com.goodda.jejuday.pay.entity.ProductCategory;
import com.goodda.jejuday.pay.repository.ProductExchangeRepository;
import com.goodda.jejuday.pay.repository.ProductRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it")
@DisplayName("상품 교환 동시성 통합/부하 테스트 (재고 @Version 낙관적 락 + @Retryable)")
class ProductServiceIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductExchangeRepository exchangeRepository;

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private Long productId;

    @BeforeEach
    void setup() {
        // 나머지 테스트가 남긴 데이터와 격리 (FK 순서: exchanges → products → users)
        cleanUpTables();

        Product product = productRepository.save(Product.builder()
                .name("제주 굿즈")
                .category(ProductCategory.GOODS)   // JEJU_TICON은 중복구매 차단 로직 있음 → 순수 재고경합 위해 GOODS
                .hallabongCost(1000)
                .stock(100)
                .build());
        this.productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        // 이 클래스가 만든 exchanges/users를 남겨두면 users FK를 참조하는
        // 다른 IT 클래스(예: SpotEngagementReconciliationJobIT)의 deleteAllInBatch가 실패한다.
        cleanUpTables();
    }

    private void cleanUpTables() {
        exchangeRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * 재고 stock개 상품에 threadCount명이 동시에 교환 요청 → 결과 집계.
     * Java 21 가상 스레드로 threadCount만큼 진짜 동시 실행 (플랫폼 스레드 고갈 없음).
     */
    private LoadResult runConcurrentExchange(int threadCount, int stock) throws InterruptedException {
        // 재고 세팅
        Product product = productRepository.findById(productId).orElseThrow();
        product.setStock(stock);
        productRepository.saveAndFlush(product);

        // 요청자 생성 (전원 충분한 한라봉 → 실패 원인을 재고/락으로 한정)
        // nickname 컬럼은 unique + length=20 → base36 인코딩으로 짧고 유일하게 생성
        String uniqueBase = Long.toString(System.nanoTime(), 36);
        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            String nickname = "u" + uniqueBase + Integer.toString(i, 36);
            User user = userRepository.save(User.builder()
                    .platform(Platform.APP)
                    .gender(Gender.MALE)
                    .email(nickname + "@test.com")
                    .birthYear("1990")
                    .nickname(nickname)
                    .language(Language.KOREAN)
                    .createdAt(LocalDateTime.now())
                    .hallabong(1_000_000)
                    .build());
            userIds.add(user.getId());
        }

        AtomicInteger success  = new AtomicInteger();
        AtomicInteger soldOut  = new AtomicInteger();   // 재고 소진 (정상 거절)
        AtomicInteger lockFail = new AtomicInteger();   // 락 경합(낙관적 락 충돌 + 데드락) 재시도 3회 소진
        AtomicInteger etc      = new AtomicInteger();

        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threadCount);

        // try-with-resources: 가상 스레드 executor는 close 시 전 작업 완료 대기
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Long uid : userIds) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        productService.exchangeProduct(uid, productId);
                        success.incrementAndGet();
                    } catch (OutOfStockException e) {
                        soldOut.incrementAndGet();
                    } catch (ConcurrencyFailureException e) {
                        lockFail.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        etc.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            long t0 = System.currentTimeMillis();
            start.countDown();          // 전원 동시 출발
            done.await();
            long elapsed = System.currentTimeMillis() - t0;

            int finalStock = productRepository.findById(productId).orElseThrow().getStock();
            int exchangeCount = exchangeRepository.findAll().size();

            LoadResult r = new LoadResult(success.get(), soldOut.get(), lockFail.get(),
                    etc.get(), finalStock, exchangeCount, elapsed);
            System.out.printf(
                    "%n===== 부하 결과 (재고 %d / 동시 %d명) =====%n" +
                            "성공 %d | 재고소진 %d | 락충돌(재시도소진) %d | 기타 %d%n" +
                            "최종 재고 %d | 교환 내역 %d건 | 소요 %dms | 락충돌률 %.1f%%%n",
                    stock, threadCount, r.success, r.soldOut, r.lockFail, r.etc,
                    r.finalStock, r.exchangeCount, r.elapsed,
                    r.lockFail * 100.0 / threadCount);
            return r;
        }
    }

    private record LoadResult(int success, int soldOut, int lockFail, int etc,
                              int finalStock, int exchangeCount, long elapsed) {}

    @Nested
    @DisplayName("정합성 검증")
    class Correctness {

        @Test
        @DisplayName("재고 1개 + 동시 10명 → 정확히 1명만 성공, over-selling 0건")
        void 재고1_동시10() throws InterruptedException {
            LoadResult r = runConcurrentExchange(10, 1);

            assertThat(r.success()).isEqualTo(1);
            assertThat(r.finalStock()).isZero();
            assertThat(r.exchangeCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("규모 확장 부하 테스트")
    class LoadTest {

        @Test
        @DisplayName("재고 100개 + 동시 1,000명 → over-selling 0건, 락 특성 측정")
        void 재고100_동시1000() throws InterruptedException {
            LoadResult r = runConcurrentExchange(1000, 100);

            // 핵심 불변식: 초과 판매 0건 & 유실 0건
            assertThat(r.finalStock()).isGreaterThanOrEqualTo(0);        // 음수 재고 없음
            assertThat(r.success()).isLessThanOrEqualTo(100);            // 재고 초과 성공 없음
            assertThat(r.exchangeCount()).isEqualTo(r.success());        // 교환내역 = 성공수
            assertThat(r.finalStock() + r.success()).isEqualTo(100);     // 재고+성공 = 초기재고
        }
    }
}