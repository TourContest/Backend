package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.Spot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SpotCreateRequestDTO {
    @NotBlank
    private String name;

    private String description;

    @NotNull
    private BigDecimal latitude;

    @NotNull
    private BigDecimal longitude;

    private Long themeId;   // null 이면 미선택
    private String tag1;    // null 이면 미선택
    private String tag2;
    private String tag3;
}