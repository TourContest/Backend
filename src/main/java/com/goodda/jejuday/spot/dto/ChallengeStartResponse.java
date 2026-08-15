package com.goodda.jejuday.spot.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChallengeStartResponse {
    private Long challengeId;
    private BigDecimal spotLatitude;
    private BigDecimal spotLongitude;
    private double distanceMetersToTarget; // 현재 위치→스팟까지 직선거리
    private String myStatus;               // JOINED 등
    private int point;                     // 완료 시 지급될 한라봉
}