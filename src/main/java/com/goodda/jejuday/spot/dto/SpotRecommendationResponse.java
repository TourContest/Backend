package com.goodda.jejuday.spot.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SpotRecommendationResponse {
    private Long id;
    private String name;
    private String type; // "SPOT" 또는 "CHALLENGE" - 프론트에서 챌린지 항목 구분용
    private BigDecimal latitude;
    private BigDecimal longitude;
    private double distanceMeters;
    private List<String> imageUrls;
    private String overviewSnippet;
    private String categoryName;
    private Double congestionScore; // 0(한산)~1(매우혼잡), 데이터 없으면 null
}
