package com.goodda.jejuday.notification.util;

import java.time.Duration;

public class NotificationConstants {

    // 점수 가중치 (댓글:좋아요:조회수 = 1:3:2)
    public static final int REPLY_WEIGHT = 1;
    public static final int LIKE_WEIGHT = 3;
    public static final int VIEW_WEIGHT = 2;

    // 시간 가중치 (2주 기준)
    public static final int TIME_DECAY_DAYS = 14;
    public static final double MIN_TIME_WEIGHT = 0.1;

    // 캐시 설정 - 개별 게시글 기준으로 변경
    public static final String SCORE_CACHE_KEY = "spot:score:individual:%d";
    public static final Duration SCORE_CACHE_TTL = Duration.ofMinutes(30);

    // 승격 기준
    public static final int POST_TO_SPOT_THRESHOLD = 50;
    public static final double SPOT_TO_CHALLENGE_PERCENTAGE = 0.3;

    // Redis 키
    public static final String RANKING_KEY = "community:ranking";
    public static final String PROMOTION_CACHE_KEY = "promotion:executed:%s";
    public static final Duration PROMOTION_CACHE_TTL = Duration.ofHours(1);

    public static final Duration DEFAULT_CACHE_TTL = Duration.ofMillis(100);
    public static final String CACHE_KEY_FORMAT = "NOTIFY:%d:%s:%s";

    public static final String ATTENDANCE_CACHE_KEY = "attendance:checked:%s:%d";
    public static final Duration ATTENDANCE_CACHE_TTL = Duration.ofHours(25); // 하루 + 1시간

    private NotificationConstants() {
        // 유틸리티 클래스 인스턴스화 방지
    }
}
