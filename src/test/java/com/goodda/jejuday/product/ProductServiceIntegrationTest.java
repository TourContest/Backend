package com.goodda.jejuday.product;

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
import com.goodda.jejuday.pay.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductExchangeRepository exchangeRepository;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setup() {
        // FK 순서대로 삭제 (exchanges → products → users)
        exchangeRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        User user = userRepository.save(User.builder()
                .email("test-" + System.nanoTime() + "@test.com")
                .password("password")
                .nickname("테스터-" + System.nanoTime())
                .name("테스터")
                .platform(Platform.APP)
                .gender(Gender.MALE)
                .language(Language.KOREAN)
                .birthYear("1995")
                .hallabong(10000)
                .build());
        userId = user.getId();

        Product product = productRepository.save(Product.builder()
                .name("제주 굿즈")
                .category(ProductCategory.GOODS)
                .hallabongCost(1000)
                .stock(10)
                .build());
        productId = product.getId();
    }

    @Nested
    @DisplayName("기본 기능 테스트")
    class BasicFunctionTest {

        @Test
        @DisplayName("정상 교환 - 포인트 차감 및 재고 감소 확인")
        void 정상_교환() {
            // when
            productService.exchangeProduct(userId, productId);

            // then
            User updatedUser = userRepository.findById(userId).get();
            Product updatedProduct = productRepository.findById(productId).get();

            assertThat(updatedUser.getHallabong()).isEqualTo(9000);  // 10000 - 1000
            assertThat(updatedProduct.getStock()).isEqualTo(9);       // 10 - 1
            assertThat(exchangeRepository.findByUserIdOrderByExchangedAtDesc(userId)).hasSize(1);
        }

        @Test
        @DisplayName("포인트 부족 - 차감 없이 예외 발생")
        void 포인트_부족() {
            // given
            User user = userRepository.findById(userId).get();
            user.setHallabong(500); // 비용(1000)보다 적음
            userRepository.save(user);

            // when & then
            assertThatThrownBy(() -> productService.exchangeProduct(userId, productId))
                    .isInstanceOf(InsufficientHallabongException.class);

            // 포인트·재고 변경 없음 확인
            assertThat(userRepository.findById(userId).get().getHallabong()).isEqualTo(500);
            assertThat(productRepository.findById(productId).get().getStock()).isEqualTo(10);
        }

        @Test
        @DisplayName("재고 0 - 차감 없이 예외 발생")
        void 재고_없음() {
            // given
            Product product = productRepository.findById(productId).get();
            product.setStock(0);
            productRepository.save(product);

            // when & then
            assertThatThrownBy(() -> productService.exchangeProduct(userId, productId))
                    .isInstanceOf(OutOfStockException.class);

            // 포인트 차감 없음 확인
            assertThat(userRepository.findById(userId).get().getHallabong()).isEqualTo(10000);
        }

        @Test
        @DisplayName("제주티콘 중복 구매 방지")
        void 제주티콘_중복_구매() {
            // given
            Product ticon = productRepository.save(Product.builder()
                    .name("제주티콘")
                    .category(ProductCategory.JEJU_TICON)
                    .hallabongCost(1000)
                    .stock(100)
                    .build());

            // 1번 구매
            productService.exchangeProduct(userId, ticon.getId());

            // when & then: 2번 구매 시 예외
            assertThatThrownBy(() -> productService.exchangeProduct(userId, ticon.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("이미 구매한 제주티콘은 중복 구매할 수 없습니다.");

            // 구매 내역은 1건만 존재
            assertThat(exchangeRepository.findByUserIdOrderByExchangedAtDesc(userId)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("동시성 테스트 - 낙관적 락")
    class ConcurrencyTest {

        @Test
        @DisplayName("재고 1개 + 10명 동시 요청 → Over-selling 0건")
        void 재고_1개_동시_10명_Over_selling_방지() throws InterruptedException {
            // given: 재고 1개로 설정
            Product product = productRepository.findById(productId).get();
            product.setStock(1);
            productRepository.save(product);

            List<Long> userIds = createUsers(10);

            int threadCount = 10;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final Long uid = userIds.get(i);
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(); // 모든 스레드 준비 후 동시 시작
                        productService.exchangeProduct(uid, productId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();     // 모든 스레드 준비 완료 대기
            start.countDown(); // 동시 시작 신호
            done.await();      // 전부 완료 대기
            executor.shutdown();

            // then
            Product finalProduct = productRepository.findById(productId).get();

            System.out.println("===== 동시성 테스트 결과 (재고 1개 / 10명) =====");
            System.out.println("성공: " + successCount.get() + "건");
            System.out.println("실패(락 충돌·재고부족): " + failCount.get() + "건");
            System.out.println("최종 재고: " + finalProduct.getStock());
            System.out.println("교환 내역: " + exchangeRepository.findAll().size() + "건");

            // 핵심 검증 1: 재고 음수 없음 (Over-selling 방지)
            assertThat(finalProduct.getStock()).isGreaterThanOrEqualTo(0);

            // 핵심 검증 2: 성공 수만큼만 재고 감소
            assertThat(finalProduct.getStock()).isEqualTo(1 - successCount.get());

            // 핵심 검증 3: 교환 내역 수 = 성공 수
            assertThat(exchangeRepository.findAll()).hasSize(successCount.get());
        }

        @Test
        @DisplayName("재고 5개 + 20명 동시 요청 → 재고 음수 없음, 성공 최대 5건")
        void 재고_5개_동시_20명() throws InterruptedException {
            // given
            Product product = productRepository.findById(productId).get();
            product.setStock(5);
            productRepository.save(product);

            List<Long> userIds = createUsers(20);

            int threadCount = 20;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final Long uid = userIds.get(i);
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        productService.exchangeProduct(uid, productId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            done.await();
            executor.shutdown();

            // then
            Product finalProduct = productRepository.findById(productId).get();

            System.out.println("===== 동시성 테스트 결과 (재고 5개 / 20명) =====");
            System.out.println("성공: " + successCount.get() + "건 (최대 5건)");
            System.out.println("실패: " + failCount.get() + "건");
            System.out.println("최종 재고: " + finalProduct.getStock());

            // 재고 음수 없음
            assertThat(finalProduct.getStock()).isGreaterThanOrEqualTo(0);

            // 재고 이상 성공 불가
            assertThat(successCount.get()).isLessThanOrEqualTo(5);
        }
    }

    @Nested
    @DisplayName("구매 내역 조회 테스트")
    class HistoryTest {

        @Test
        @DisplayName("구매 내역 Fetch Join - N+1 없이 상품 정보 포함 조회")
        void 구매내역_fetch_join_조회() {
            // given: 상품 3개 구매
            for (int i = 0; i < 3; i++) {
                Product p = productRepository.save(Product.builder()
                        .name("상품" + i)
                        .category(ProductCategory.GOODS)
                        .hallabongCost(100)
                        .stock(10)
                        .build());
                productService.exchangeProduct(userId, p.getId());
            }

            // when: Fetch Join 조회 (N+1 없이 1번 쿼리)
            var history = productService.getUserProductHistory(userId);

            // then
            assertThat(history).hasSize(3);
            history.forEach(h -> assertThat(h.getName()).isNotNull());
        }

        @Test
        @DisplayName("미수령 상품 조회 - accepted=false 필터링")
        void 미수령_상품_조회() {
            // given
            productService.exchangeProduct(userId, productId);

            // when
            var unaccepted = productService.getUserUnacceptedProductHistory(userId);

            // then
            assertThat(unaccepted).hasSize(1);
            assertThat(unaccepted.get(0).isAccepted()).isFalse();
        }

        @Test
        @DisplayName("카테고리별 상품 목록 조회")
        void 카테고리별_상품_조회() {
            // given: JEJU_TICON 상품 2개 추가
            productRepository.save(Product.builder()
                    .name("제주티콘A")
                    .category(ProductCategory.JEJU_TICON)
                    .hallabongCost(500)
                    .stock(10)
                    .build());
            productRepository.save(Product.builder()
                    .name("제주티콘B")
                    .category(ProductCategory.JEJU_TICON)
                    .hallabongCost(500)
                    .stock(10)
                    .build());

            // when
            var ticonList = productService.getProductsByCategory(ProductCategory.JEJU_TICON);
            var goodsList = productService.getProductsByCategory(ProductCategory.GOODS);

            // then
            assertThat(ticonList).hasSize(2);
            assertThat(goodsList).hasSize(1); // setup에서 생성한 GOODS 1개
        }
    }

    private List<Long> createUsers(int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User u = userRepository.save(User.builder()
                    .email("user-" + System.nanoTime() + "-" + i + "@test.com")
                    .password("password")
                    .nickname("u" + i + "-" + (System.nanoTime() % 100000))
                    .name("테스트유저" + i)
                    .platform(Platform.APP)
                    .gender(Gender.MALE)
                    .language(Language.KOREAN)
                    .birthYear("1995")
                    .hallabong(10000)
                    .build());
            ids.add(u.getId());
        }
        return ids;
    }
}
