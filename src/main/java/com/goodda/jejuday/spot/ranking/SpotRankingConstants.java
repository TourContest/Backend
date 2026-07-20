package com.goodda.jejuday.spot.ranking;

public class SpotRankingConstants {

    // 점수 가중치 (댓글:좋아요:조회수 = 1:3:2) — SpotScoreCalculator 사용
    // 조회수는 판정용 engagementScore에는 포함하지 않는다 (수동적/조작 가능 신호라 "반응 인증" 근거로 부적격).
    // 정렬용 hotScore의 order 항에는 포함해 피드 정렬 풍부함을 유지한다.
    public static final int REPLY_WEIGHT = 1;
    public static final int LIKE_WEIGHT = 3;
    public static final int VIEW_WEIGHT = 2;

    // POST → SPOT 승격 하한선(floor) — SpotPromotionService 사용
    // engagementScore(조회 제외) 기준 20 = 좋아요 7개 상당(또는 좋아요6+댓글2, 좋아요4+댓글8 등).
    // 트래픽 데이터 없이 정한 추정치 — 운영 데이터가 쌓이면 일일 승격률 기준으로 튜닝할 것.
    public static final int POST_TO_SPOT_ENGAGEMENT_FLOOR = 20;

    // SPOT → CHALLENGE 승격 — 고정 상위 K개 (기존 min(_, 2) 하드캡의 실제 동작을 정직하게 상수명에 반영)
    public static final int SPOT_TO_CHALLENGE_TOP_K = 2;

    // Redis 키 — SpotPromotionService/SpotRankingUpdater 사용
    public static final String RANKING_KEY = "community:ranking";       // 정렬용 hotScore ZSet
    public static final String ENGAGEMENT_KEY = "community:engagement"; // 판정용 engagementScore ZSet

    private SpotRankingConstants() {}
}
