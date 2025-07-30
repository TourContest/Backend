package com.goodda.jejuday.pay.repository;

import com.goodda.jejuday.pay.entity.ProductExchange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductExchangeRepository extends JpaRepository<ProductExchange, Long> {
    List<ProductExchange> findByUserIdOrderByExchangedAtDesc(Long userId);

    List<ProductExchange> findByUserIdAndAcceptedFalseOrderByExchangedAtDesc(Long userId);
}
