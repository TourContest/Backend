package com.goodda.jejuday.pay.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.common.exception.OutOfStockException;
import com.goodda.jejuday.pay.dto.ProductDetailDto;
import com.goodda.jejuday.pay.dto.ProductDto;
import com.goodda.jejuday.pay.entity.LedgerReason;
import com.goodda.jejuday.pay.entity.Product;
import com.goodda.jejuday.pay.entity.ProductCategory;
import com.goodda.jejuday.pay.entity.ProductExchange;
import com.goodda.jejuday.pay.repository.ProductExchangeRepository;
import com.goodda.jejuday.pay.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductExchangeRepository exchangeRepository;
    private final PointLedgerService pointLedgerService;

    /**
     * 상품 교환.
     *
     * <p>동시성 전략:
     * - hallabong 차감: PointLedgerService.record 경유 — 원장 insert(exchangeId를 멱등 키로) +
     *   조건부 UPDATE가 한 트랜잭션. 잔액 부족이면 InsufficientHallabongException → 트랜잭션 전체
     *   롤백(재고 차감, 교환 내역 insert 포함).
     * - 재고 차감: Product.@Version 낙관적 락
     * - ConcurrencyFailureException(OptimisticLockingFailureException + 데드락으로 인한
     *   CannotAcquireLockException 공통 상위)은 커밋/실행 시점에 발생하므로 메서드 레벨 @Retryable로 처리.
     *   같은 상품 행에 다수 트랜잭션이 몰리면 락 순서 차이로 MySQL 데드락(1213)도 발생할 수 있어
     *   낙관적 락 충돌뿐 아니라 데드락도 함께 재시도 대상에 포함한다.
     *   @Retryable이 외부 프록시, @Transactional이 내부 프록시 순서로 동작하므로
     *   롤백 후 재시도 시 원상태로 복구됨.
     *
     * <p>멱등 키로 exchangeId(교환 건마다 새로 발급되는 PK)를 쓰기 때문에, 같은 상품을 여러 번
     * 정당하게 구매하는 것(GOODS 카테고리)까지 막지 않으면서도 재시도 시 중복 차감을 막는다.
     * 이를 위해 exchange row를 먼저 저장해 ID를 발급받은 뒤 잔액을 차감하는 순서로 진행한다.
     */
    @Retryable(
            retryFor = ConcurrencyFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50)
    )
    @Transactional
    @CacheEvict(value = {"product", "productsByCategory"}, allEntries = true)
    public void exchangeProduct(Long userId, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품 없음"));

        if (product.getCategory() == ProductCategory.JEJU_TICON &&
                exchangeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new IllegalStateException("이미 구매한 제주티콘은 중복 구매할 수 없습니다.");
        }

        if (product.getStock() <= 0) {
            throw new OutOfStockException("상품 재고 부족");
        }

        // 재고 감소 (Product.@Version 낙관적 락 — 충돌 시 커밋 시점에 OptimisticLockingFailureException)
        product.setStock(product.getStock() - 1);

        User userRef = userRepository.getReferenceById(userId);
        ProductExchange exchange = ProductExchange.builder()
                .user(userRef)
                .product(product)
                .exchangedAt(LocalDateTime.now())
                .build();
        exchangeRepository.save(exchange); // IDENTITY 전략 — save() 시점에 즉시 ID 발급됨

        // hallabong 원자적 차감 — 잔액 부족 시 InsufficientHallabongException → 트랜잭션 롤백
        pointLedgerService.record(userId, -product.getHallabongCost(), LedgerReason.PRODUCT_EXCHANGE,
                exchange.getId(), String.valueOf(exchange.getId()));

        log.info("상품 교환 완료: 사용자={}, 상품={}, 카테고리={}, 비용={}한라봉",
                userId, product.getName(), product.getCategory(), product.getHallabongCost());
    }

    @Cacheable(value = "product", key = "#productId")
    public ProductDto getProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품 없음"));
        return ProductDto.from(product);
    }

    @Cacheable(value = "productsByCategory", key = "#category")
    public List<ProductDto> getProductsByCategory(ProductCategory category) {
        return productRepository.findByCategory(category).stream()
                .map(ProductDto::from)
                .toList();
    }

    public List<ProductDetailDto> getUserProductHistory(Long userId) {
        return exchangeRepository.findByUserIdOrderByExchangedAtDesc(userId).stream()
                .map(ProductDetailDto::from)
                .toList();
    }

    @Cacheable(value = "productExchangeDetail", key = "#exchangeId")
    public ProductDetailDto getProductDetailByExchange(Long exchangeId) {
        ProductExchange exchange = exchangeRepository.findWithProductById(exchangeId)
                .orElseThrow(() -> new EntityNotFoundException("교환 내역 없음"));
        return ProductDetailDto.from(exchange);
    }

    @Transactional
    public void toggleProductAccepted(Long exchangeId) {
        ProductExchange exchange = exchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new EntityNotFoundException("교환 기록 없음"));
        exchange.setAccepted(!exchange.isAccepted());
        exchangeRepository.save(exchange);
    }

    public List<ProductDetailDto> getUserUnacceptedProductHistory(Long userId) {
        return exchangeRepository.findByUserIdAndAcceptedFalseOrderByExchangedAtDesc(userId).stream()
                .map(ProductDetailDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductDetailDto> getUserProductHistoryByCategory(Long userId, ProductCategory category) {
        return exchangeRepository.findByUserIdOrderByExchangedAtDesc(userId).stream()
                .filter(exchange -> exchange.getProduct().getCategory() == category)
                .map(ProductDetailDto::from)
                .toList();
    }
}
