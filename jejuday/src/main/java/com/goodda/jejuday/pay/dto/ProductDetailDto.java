package com.goodda.jejuday.pay.dto;

import com.goodda.jejuday.pay.entity.Product;
import com.goodda.jejuday.pay.entity.ProductCategory;
import com.goodda.jejuday.pay.entity.ProductExchange;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDetailDto {
    private Long id;
    private String name;
    private String imageUrl;
    private ProductCategory category;
    private boolean accepted;

    public static ProductDetailDto from(ProductExchange exchange) {
        Product product = exchange.getProduct();
        return ProductDetailDto.builder()
                .id(product.getId())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .category(product.getCategory())
                .accepted(exchange.isAccepted())
                .build();
    }
}
