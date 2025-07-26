package com.goodda.jejuday.spot.dto;

import lombok.Data;
import lombok.Getter;

import java.math.BigDecimal;

@Data
@Getter
public class SpotUpdateRequest {
    private String name;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
}