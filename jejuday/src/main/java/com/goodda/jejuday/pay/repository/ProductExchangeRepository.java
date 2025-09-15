package com.goodda.jejuday.pay.repository;

import com.goodda.jejuday.pay.entity.ProductExchange;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductExchangeRepository extends JpaRepository<ProductExchange, Long> {
    @Query("SELECT pe FROM ProductExchange pe JOIN FETCH pe.product WHERE pe.user.id = :userId ORDER BY pe.exchangedAt DESC")
    List<ProductExchange> findByUserIdOrderByExchangedAtDesc(Long userId);

    @Query("SELECT pe FROM ProductExchange pe JOIN FETCH pe.product WHERE pe.user.id = :userId AND pe.accepted = false ORDER BY pe.exchangedAt DESC")
    List<ProductExchange> findByUserIdAndAcceptedFalseOrderByExchangedAtDesc(Long userId);

    @Query("SELECT pe FROM ProductExchange pe JOIN FETCH pe.product WHERE pe.id = :exchangeId")
    Optional<ProductExchange> findWithProductById(Long exchangeId);

    @Query("SELECT COUNT(pe) > 0 FROM ProductExchange pe WHERE pe.user.id = :userId AND pe.product.id = :productId")
    boolean existsByUserIdAndProductId(Long userId, Long productId);
}
