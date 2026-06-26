package com.goodda.jejuday.spot.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChallengeStartRequest {
    private BigDecimal latitude;   // 현재 사용자 위도
    private BigDecimal longitude;  // 현재 사용자 경도
}