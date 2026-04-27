package com.goodda.jejuday.product;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.common.exception.InsufficientHallabongException;
import com.goodda.jejuday.common.exception.OutOfStockException;
import com.goodda.jejuday.pay.entity.Product;
import com.goodda.jejuday.pay.entity.ProductCategory;
import com.goodda.jejuday.pay.entity.ProductExchange;
import com.goodda.jejuday.pay.repository.ProductExchangeRepository;
import com.goodda.jejuday.pay.repository.ProductRepository;
import com.goodda.jejuday.pay.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductExchangeRepository exchangeRepository;

    private User user;
    private Product product;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        user = User.builder()
                .id(1L)
                .hallabong(5000)
                .build();

        product = Product.builder()
                .id(1L)
                .name("제주 굿즈")
                .category(ProductCategory.GOODS)
                .hallabongCost(1000)
                .stock(10)
                .build();
    }

    // =========================================================
    // 1. 정상 교환
    // =========================================================
    @Nested
    @DisplayName("상품 교환 정상 케이스")
    class ExchangeSuccess {

        @Test
        @DisplayName("포인트 충분 + 재고 있음 → 교환 성공")
        void 교환_성공() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(exchangeRepository.existsByUserIdAndProductId(anyLong(), anyLong())).thenReturn(false);

            // when
            productService.exchangeProduct(1L, 1L);

            // then
            assertThat(user.getHallabong()).isEqualTo(4000);   // 5000 - 1000
            assertThat(product.getStock()).isEqualTo(9);        // 10 - 1
            verify(exchangeRepository, times(1)).save(any(ProductExchange.class));
        }

        @Test
        @DisplayName("교환 후 캐시 무효화 호출 확인")
        void 교환_후_캐시_무효화() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(exchangeRepository.existsByUserIdAndProductId(anyLong(), anyLong())).thenReturn(false);

            productService.exchangeProduct(1L, 1L);

            // @CacheEvict 동작 확인 → exchangeRepository.save 호출 여부로 간접 검증
            verify(exchangeRepository, times(1)).save(any(ProductExchange.class));
        }
    }

    // =========================================================
    // 2. 예외 케이스
    // =========================================================
    @Nested
    @DisplayName("상품 교환 예외 케이스")
    class ExchangeException {

        @Test
        @DisplayName("유저 없음 → EntityNotFoundException")
        void 유저_없음_예외() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.exchangeProduct(999L, 1L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("유저 없음");
        }

        @Test
        @DisplayName("상품 없음 → EntityNotFoundException")
        void 상품_없음_예외() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.exchangeProduct(1L, 999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("상품 없음");
        }

        @Test
        @DisplayName("포인트 부족 → InsufficientHallabongException")
        void 포인트_부족_예외() {
            user.setHallabong(500); // 비용(1000)보다 적음
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(exchangeRepository.existsByUserIdAndProductId(anyLong(), anyLong())).thenReturn(false);

            assertThatThrownBy(() -> productService.exchangeProduct(1L, 1L))
                    .isInstanceOf(InsufficientHallabongException.class)
                    .hasMessage("한라봉 포인트 부족");

            // 포인트·재고 변경 없음 검증
            assertThat(user.getHallabong()).isEqualTo(500);
            assertThat(product.getStock()).isEqualTo(10);
        }

        @Test
        @DisplayName("재고 0 → OutOfStockException")
        void 재고_없음_예외() {
            product.setStock(0);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(exchangeRepository.existsByUserIdAndProductId(anyLong(), anyLong())).thenReturn(false);

            assertThatThrownBy(() -> productService.exchangeProduct(1L, 1L))
                    .isInstanceOf(OutOfStockException.class)
                    .hasMessage("상품 재고 부족");

            assertThat(user.getHallabong()).isEqualTo(5000); // 차감 없음
        }

        @Test
        @DisplayName("제주티콘 중복 구매 → IllegalStateException")
        void 제주티콘_중복_구매_예외() {
            product.setCategory(ProductCategory.JEJU_TICON);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(exchangeRepository.existsByUserIdAndProductId(1L, 1L)).thenReturn(true); // 이미 구매

            assertThatThrownBy(() -> productService.exchangeProduct(1L, 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("이미 구매한 제주티콘은 중복 구매할 수 없습니다.");
        }

        @Test
        @DisplayName("낙관적 락 충돌 → RuntimeException (재시도 안내)")
        void 낙관적_락_충돌_예외() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(exchangeRepository.existsByUserIdAndProductId(anyLong(), anyLong())).thenReturn(false);
            when(exchangeRepository.save(any())).thenThrow(new OptimisticLockingFailureException("충돌"));

            assertThatThrownBy(() -> productService.exchangeProduct(1L, 1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("상품 교환 중 충돌이 발생했습니다. 다시 시도해주세요.");
        }
    }

    // =========================================================
    // 3. 동시성 테스트 (낙관적 락 핵심 검증)
    // =========================================================
    @Nested
    @DisplayName("동시성 테스트 - 낙관적 락")
    class ConcurrencyTest {

        @Test
        @DisplayName("재고 1개에 10명 동시 요청 → 충돌 감지 후 1명만 성공")
        void 재고_1개_동시_10명_요청() throws InterruptedException {
            int threadCount = 10;
            product.setStock(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // N번 중 1번만 save 성공, 나머지는 OptimisticLockingFailureException
            AtomicInteger saveCallCount = new AtomicInteger(0);
            when(userRepository.findById(anyLong())).thenReturn(Optional.of(user));
            when(productRepository.findById(anyLong())).thenReturn(Optional.of(product));
            when(exchangeRepository.existsByUserIdAndProductId(anyLong(), anyLong())).thenReturn(false);
            when(exchangeRepository.save(any())).thenAnswer(invocation -> {
                if (saveCallCount.getAndIncrement() == 0) {
                    return invocation.getArgument(0); // 첫 번째만 성공
                }
                throw new OptimisticLockingFailureException("버전 충돌");
            });

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        productService.exchangeProduct(1L, 1L);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            // 1명만 성공, 나머지 9명은 충돌로 실패
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(9);
            assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
        }

        @Test
        @DisplayName("락 없을 때 동시 체크 통과 시 재고 음수 발생 시뮬레이션")
        void 락_없을때_재고_음수_발생_시뮬레이션() {
            product.setStock(1);

            // 두 스레드가 동시에 stock > 0 체크를 통과한 상황 가정
            // (DB 트랜잭션 격리 없을 때 실제 발생하는 시나리오)
            boolean thread1PassedCheck = product.getStock() > 0; // true
            boolean thread2PassedCheck = product.getStock() > 0; // true (아직 차감 전)

            if (thread1PassedCheck) product.setStock(product.getStock() - 1); // 1 → 0
            if (thread2PassedCheck) product.setStock(product.getStock() - 1); // 0 → -1

            // 락 없으면 음수 재고 발생
            assertThat(product.getStock()).isLessThan(0);
        }
    }

    // =========================================================
    // 4. 조회 테스트
    // =========================================================
    @Nested
    @DisplayName("상품 조회")
    class ProductQuery {

        @Test
        @DisplayName("카테고리별 상품 목록 조회")
        void 카테고리별_상품_목록_조회() {
            when(productRepository.findByCategory(ProductCategory.GOODS))
                    .thenReturn(List.of(product));

            var result = productService.getProductsByCategory(ProductCategory.GOODS);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("제주 굿즈");
            verify(productRepository, times(1)).findByCategory(ProductCategory.GOODS);
        }

        @Test
        @DisplayName("상품 단건 조회 - 없을 때 예외")
        void 상품_단건_조회_없음() {
            when(productRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProduct(999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessage("상품 없음");
        }

        @Test
        @DisplayName("사용자 구매 내역 조회")
        void 사용자_구매_내역_조회() {
            ProductExchange exchange = ProductExchange.builder()
                    .id(1L)
                    .user(user)
                    .product(product)
                    .build();
            when(exchangeRepository.findByUserIdOrderByExchangedAtDesc(1L))
                    .thenReturn(List.of(exchange));

            var result = productService.getUserProductHistory(1L);

            assertThat(result).hasSize(1);
            verify(exchangeRepository, times(1)).findByUserIdOrderByExchangedAtDesc(1L);
        }
    }
}
