package com.goodda.jejuday.spot.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChallengeCompleteRequest {
    private BigDecimal latitude;   // 현재 사용자 위도
    private BigDecimal longitude;  // 현재 사용자 경도
    private String proofUrl;    // TODO: 인증 사진(리뷰 테이블 연동 시 사용)
}