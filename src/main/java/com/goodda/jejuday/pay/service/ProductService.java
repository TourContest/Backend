package com.goodda.jejuday.pay.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.common.exception.InsufficientGradeException;
import com.goodda.jejuday.common.exception.InsufficientHallabongException;
import com.goodda.jejuday.common.exception.OutOfStockException;
import com.goodda.jejuday.pay.dto.ProductDetailDto;
import com.goodda.jejuday.pay.dto.ProductDto;
import com.goodda.jejuday.pay.entity.Product;
import com.goodda.jejuday.pay.entity.ProductCategory;
import com.goodda.jejuday.pay.entity.ProductExchange;
import com.goodda.jejuday.pay.repository.ProductExchangeRepository;
import com.goodda.jejuday.pay.repository.ProductRepository;
import com.goodda.jejuday.steps.entity.MoodGrade;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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

//        // 굿즈 구매 등급 제한 검사
//        if (product.getCategory() == ProductCategory.GOODS) {
//            MoodGrade currentGrade = user.getMoodGrade();
//            if (!currentGrade.canPurchaseGoods()) {
//                long stepsNeeded = MoodGrade.getStepsNeededForGoods(user.getTotalSteps());
//                throw new InsufficientGradeException(
//                        String.format("굿즈 구매는 오름꾼 등급(4만보) 이상부터 가능합니다. " +
//                                        "현재 등급: %s, 필요한 걸음수: %d보",
//                                currentGrade.getDisplayName(), stepsNeeded)
//                );
//            }
//            log.info("굿즈 구매 등급 확인 통과: 사용자={}, 등급={}, 총걸음수={}보",
//                    userId, currentGrade.getDisplayName(), user.getTotalSteps());
//        }

        if (product.getCategory() == ProductCategory.JEJU_TICON &&
                exchangeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new IllegalStateException("이미 구매한 제주티콘은 중복 구매할 수 없습니다.");
        }

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
            log.info("상품 교환 완료: 사용자={}, 상품={}, 카테고리={}, 비용={}한라봉",
                    userId, product.getName(), product.getCategory(), product.getHallabongCost());
        } catch (OptimisticLockingFailureException e) {
            throw new RuntimeException("상품 교환 중 충돌이 발생했습니다. 다시 시도해주세요.");
        }
    }

//    /**
//     * 사용자가 굿즈를 구매할 수 있는지 확인
//     */
//    public boolean canUserPurchaseGoods(Long userId) {
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new EntityNotFoundException("유저 없음"));
//        return user.getMoodGrade().canPurchaseGoods();
//    }
//
//    /**
//     * 굿즈 구매까지 필요한 걸음수 조회
//     */
//    public long getStepsNeededForGoods(Long userId) {
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new EntityNotFoundException("유저 없음"));
//        return MoodGrade.getStepsNeededForGoods(user.getTotalSteps());
//    }

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

//    /**
//     * 사용자 등급에 따른 구매 가능한 상품 목록 조회
//     */
//    public List<ProductDto> getAvailableProductsByCategory(Long userId, ProductCategory category) {
//        List<ProductDto> products = getProductsByCategory(category);
//
//        // 굿즈 카테고리인 경우 등급 제한 확인
//        if (category == ProductCategory.GOODS) {
//            boolean canPurchaseGoods = canUserPurchaseGoods(userId);
//            if (!canPurchaseGoods) {
//                // 굿즈 구매 자격이 없는 경우 빈 리스트 반환하거나 필터링
//                log.info("사용자 {}는 굿즈 구매 자격이 없어 빈 목록 반환", userId);
//                return List.of(); // 또는 예외를 던질 수도 있음
//            }
//        }
//
//        return products;
//    }

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
