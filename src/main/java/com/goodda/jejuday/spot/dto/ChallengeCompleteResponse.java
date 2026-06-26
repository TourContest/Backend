package com.goodda.jejuday.spot.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChallengeCompleteResponse {
    private Long challengeId;
    private boolean withinThreshold;   // 임계 반경 내 도달 여부
    private double distanceMetersToTarget;
    private int awardedPoints;         // 지급 포인트 (spot.point)
    private int myHallabongAfter;      // 지급 후 보유 한라봉
    private LocalDateTime completedAt;
}