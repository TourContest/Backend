package com.goodda.jejuday.spot.ranking;

import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.LIKE_WEIGHT;
import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.REPLY_WEIGHT;

import org.springframework.stereotype.Component;

/**
 * 승격 판정용 engagement 점수의 단일 정의. DB/Redis 에 접근하지 않는 순수 계산기이며 호출자가 DB 실측 카운트를 넘긴다.
 * (정렬용 hot 점수와 Redis ZSet 은 제거됐다 — 피드 정렬은 DB ORDER BY 를 쓴다)
 */
@Component
public class SpotScoreCalculator {

    /** 판정용: 시간 미포함, 조회수 제외(조작 가능 신호이므로 승격 판정 근거에서 배제). likes 는 spot.like_count. */
    public int engagementScore(int likes, int replies) {
        return replies * REPLY_WEIGHT + likes * LIKE_WEIGHT;
    }
}
