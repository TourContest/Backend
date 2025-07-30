package com.goodda.jejuday.pay.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.common.exception.InsufficientHallabongException;
import com.goodda.jejuday.common.exception.OutOfStockException;
import com.goodda.jejuday.pay.dto.ProductDetailDto;
import com.goodda.jejuday.pay.dto.ProductDto;
import com.goodda.jejuday.pay.entity.Product;
import com.goodda.jejuday.pay.entity.ProductCategory;
import com.goodda.jejuday.pay.entity.ProductExchange;
import com.goodda.jejuday.pay.repository.ProductExchangeRepository;
import com.goodda.jejuday.pay.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductExchangeRepository exchangeRepository;

    @Transactional
    @CacheEvict(value = {"product", "productsByCategory"}, allEntries = true)
    public void exchangeProduct(Long userId, Long productId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("유저 없음"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품 없음"));

        if (user.getHallabong() < product.getHallabongCost()) {
            throw new InsufficientHallabongException("한라봉 포인트 부족");
        }

        if (product.getStock() <= 0) {
            throw new OutOfStockException("상품 재고 부족");
        }

        // 포인트 차감 & 재고 감소
        user.setHallabong(user.getHallabong() - product.getHallabongCost());
        product.setStock(product.getStock() - 1);

        ProductExchange exchange = ProductExchange.builder()
                .user(user)
                .product(product)
                .exchangedAt(LocalDateTime.now())
                .build();

        try {
            exchangeRepository.save(exchange);
            // 낙관적 락 충돌은 트랜잭션 커밋 시 발생
        } catch (OptimisticLockingFailureException e) {
            throw new RuntimeException("상품 교환 중 충돌이 발생했습니다. 다시 시도해주세요.");
        }
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
        ProductExchange exchange = exchangeRepository.findById(exchangeId)
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
}
