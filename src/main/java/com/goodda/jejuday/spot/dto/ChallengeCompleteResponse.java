package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.mission.dto.CompletedMissionResponse;
import java.time.LocalDateTime;
import java.util.List;
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
    private List<CompletedMissionResponse> completedMissions; // 이번 완료로 새로 완주된 미션 (없으면 빈 배열)
}