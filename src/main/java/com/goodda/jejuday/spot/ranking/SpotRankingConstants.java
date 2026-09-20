package com.goodda.jejuday.spot.ranking;

public class SpotRankingConstants {

    // 점수 가중치 (댓글:좋아요 = 1:3) — SpotScoreCalculator 사용
    // 조회수는 판정용 engagementScore에 포함하지 않는다 (수동적/조작 가능 신호라 "반응 인증" 근거로 부적격).
    public static final int REPLY_WEIGHT = 1;
    public static final int LIKE_WEIGHT = 3;

    // POST → SPOT 승격 하한선(floor) — SpotPromotionService 사용
    // engagementScore(조회 제외) 기준 10 = 좋아요 3~4개 상당(또는 좋아요2+댓글4 등).
    // 승격이 너무 드물어 20에서 10으로 하향 조정 — 운영 데이터가 쌓이면 일일 승격률 기준으로 재튜닝할 것.
    public static final int POST_TO_SPOT_ENGAGEMENT_FLOOR = 10;

    // SPOT → CHALLENGE 승격 — 고정 상위 K개 (기존 min(_, 2) 하드캡의 실제 동작을 정직하게 상수명에 반영)
    public static final int SPOT_TO_CHALLENGE_TOP_K = 2;

    private SpotRankingConstants() {}
}
