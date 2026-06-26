package com.goodda.jejuday.notification.util;

import java.time.Duration;

public class NotificationConstants {

    // 점수 가중치 (댓글:좋아요:조회수 = 1:3:2) — SpotScoreCalculator 사용
    public static final int REPLY_WEIGHT = 1;
    public static final int LIKE_WEIGHT = 3;
    public static final int VIEW_WEIGHT = 2;

    // 시간 가중치 (2주 기준) — SpotScoreCalculator 사용
    public static final int TIME_DECAY_DAYS = 14;
    public static final double MIN_TIME_WEIGHT = 0.1;

    // 개별 스팟 점수 캐시 — SpotScoreCalculator 사용
    public static final String SCORE_CACHE_KEY = "spot:score:individual:%d";
    public static final Duration SCORE_CACHE_TTL = Duration.ofMinutes(30);

    // 승격 기준 — SpotPromotionService 사용
    public static final int POST_TO_SPOT_THRESHOLD = 50;
    public static final double SPOT_TO_CHALLENGE_PERCENTAGE = 0.3;

    // Redis 키 — SpotPromotionService 사용
    public static final String RANKING_KEY = "community:ranking";
    public static final String PROMOTION_CACHE_KEY = "promotion:executed:%s";
    public static final Duration PROMOTION_CACHE_TTL = Duration.ofHours(1);

    private NotificationConstants() {}
}
