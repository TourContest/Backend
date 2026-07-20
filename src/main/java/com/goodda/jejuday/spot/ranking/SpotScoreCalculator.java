package com.goodda.jejuday.spot.ranking;

import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.LIKE_WEIGHT;
import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.REPLY_WEIGHT;
import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.VIEW_WEIGHT;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * 정렬(hot 점수)과 판정(인게이지먼트)을 분리한 순수 계산기.
 * DB/Redis에 접근하지 않으며, 호출자가 최신 카운트를 넘겨준다.
 */
@Component
public class SpotScoreCalculator {

    private static final LocalDateTime EPOCH_2020 = LocalDateTime.of(2020, 1, 1, 0, 0);
    private static final double GRAVITY = 45000.0; // 시간 감쇠 상수 — 튜닝 포인트

    /** 판정용: 시간 미포함, 조회수 제외(조작 가능 신호이므로 승격 판정 근거에서 배제). */
    public int engagementScore(int likes, int replies) {
        return replies * REPLY_WEIGHT + likes * LIKE_WEIGHT;
    }

    /** 정렬용: Reddit 스타일 hot 점수. ZSet 순서 결정에만 사용 — 절대값 비교 금지. */
    public double hotScore(int likes, int replies, int views, LocalDateTime createdAt) {
        int rankingEngagement = replies * REPLY_WEIGHT + likes * LIKE_WEIGHT + views * VIEW_WEIGHT;
        double order = Math.log10(Math.max(rankingEngagement, 1));
        long seconds = Duration.between(EPOCH_2020, createdAt).getSeconds();
        return order + seconds / GRAVITY;
    }
}
