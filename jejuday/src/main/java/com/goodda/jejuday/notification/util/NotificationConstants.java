package com.goodda.jejuday.notification.util;

import java.time.Duration;
import java.time.LocalDate;

public final class NotificationConstants {

    // ─── FCM ──────────────────────────────────────────────────
    public static final String FCM_DEFAULT_TITLE = "제주데이";
    public static final int    MIN_FCM_TOKEN_LENGTH = 20;
    public static final int    FCM_TIMEOUT_SECONDS  = 10;

    // ─── 점수 가중치 (댓글:좋아요:조회수 = 1:3:2) ───────────
    public static final int    REPLY_WEIGHT    = 1;
    public static final int    LIKE_WEIGHT     = 3;
    public static final int    VIEW_WEIGHT     = 2;

    // ─── 시간 가중치 ──────────────────────────────────────────
    public static final int    TIME_DECAY_DAYS = 14;
    public static final double MIN_TIME_WEIGHT = 0.1;

    // ─── 승격 기준 ────────────────────────────────────────────
    public static final int    POST_TO_SPOT_THRESHOLD       = 50;
    public static final double SPOT_TO_CHALLENGE_PERCENTAGE = 0.3;

    // ─── 알림 캐시 TTL ────────────────────────────────────────
    public static final Duration DEFAULT_CACHE_TTL   = Duration.ofMillis(100);
    public static final Duration SCORE_CACHE_TTL     = Duration.ofMinutes(30);
    public static final Duration PROMOTION_CACHE_TTL = Duration.ofHours(1);
    public static final Duration ATTENDANCE_CACHE_TTL = Duration.ofHours(25);

    // ─── Redis 키 포맷 ────────────────────────────────────────
    public static final String CACHE_KEY_FORMAT       = "NOTIFY:%d:%s:%s";
    public static final String SCORE_CACHE_KEY        = "spot:score:individual:%d";
    public static final String RANKING_KEY            = "community:ranking";
    public static final String PROMOTION_CACHE_KEY    = "promotion:executed:%s";
    public static final String ATTENDANCE_CACHE_KEY   = "attendance:checked:%s:%d";

    // ─── Redis SCAN 패턴 ─────────────────────────────────────
    public static final String ATTENDANCE_SCAN_PATTERN = "attendance:checked:%s:*";

    public static final String[] CACHE_CLEAR_PATTERNS = {
            "NOTIFY:*",
            "spot:score:*",
            "spot:likes:*",
            "spot:replies:*",
            "attendance:checked:*",
            "promotion:executed:*"
    };

    // ─── 컨텍스트 키 빌더 ─────────────────────────────────────
    public static String challengeContextKey(Long placeId) {
        return "challenge-place:" + placeId;
    }

    public static String replyContextKey(Long postId) {
        return "post:" + postId + ":reply";
    }

    public static String stepContextKey() {
        return "step-goal:" + LocalDate.now();
    }

    public static String commentContextKey(Long commentId) {
        return "comment:" + commentId;
    }

    public static String likeMilestoneContextKey(Long postId, int milestone) {
        return "like:" + postId + ":" + milestone;
    }

    public static String attendanceContextKey(LocalDate date) {
        return "attendance:" + date;
    }

    // ─── FCM 토큰 유틸 ────────────────────────────────────────
    public static boolean isValidFcmToken(String token) {
        return token != null && !token.isBlank() && token.length() > MIN_FCM_TOKEN_LENGTH;
    }

    public static String maskFcmToken(String token) {
        if (token == null || token.length() < 10) return "invalid";
        return token.substring(0, 10) + "***";
    }

    private NotificationConstants() {}
}
