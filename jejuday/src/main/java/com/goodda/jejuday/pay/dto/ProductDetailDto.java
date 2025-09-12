package com.goodda.jejuday.pay.dto;

import com.goodda.jejuday.pay.entity.Product;
import com.goodda.jejuday.pay.entity.ProductCategory;
import com.goodda.jejuday.pay.entity.ProductExchange;
import jakarta.persistence.EntityNotFoundException;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDetailDto {
    private Long exchangeId;   // 교환 내역 ID 추가
    private Long productId;    // 상품 ID도 같이 내려주면 프론트에서 유용
    private String name;
    private String imageUrl;
    private ProductCategory category;
    private boolean accepted;

    public static ProductDetailDto from(ProductExchange exchange) {
        Product product = exchange.getProduct();
        if (product == null) {
            throw new IllegalArgumentException("교환 내역에 연결된 상품이 없습니다.");
        }
        return ProductDetailDto.builder()
                .exchangeId(exchange.getId())       // 추가
                .productId(product.getId())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .category(product.getCategory())
                .accepted(exchange.isAccepted())
                .build();
    }
}
