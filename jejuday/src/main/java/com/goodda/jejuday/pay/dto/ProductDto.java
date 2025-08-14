package com.goodda.jejuday.pay.dto;

import com.goodda.jejuday.pay.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDto {
    private Long id;
    private String name;
    private String imageUrl;
    private int hallabongCost;
    private int stock;

    public static ProductDto from(Product product) {
        return ProductDto.builder()
                .id(product.getId())
                .name(product.getName())
                .imageUrl(product.getImageUrl())
                .hallabongCost(product.getHallabongCost())
                .stock(product.getStock())
                .build();
    }
}

