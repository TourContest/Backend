package com.goodda.jejuday.notification.service;

import static com.goodda.jejuday.notification.util.NotificationConstants.CACHE_KEY_FORMAT;
import static com.goodda.jejuday.notification.util.NotificationConstants.DEFAULT_CACHE_TTL;

import com.goodda.jejuday.notification.entity.NotificationType;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCacheManager {

    private final RedisTemplate<String, String> redisTemplate;

    public void markNotificationAsSent(Long userId, NotificationType type, String contextKey) {
        String cacheKey = buildCacheKey(userId, type, contextKey);
        redisTemplate.opsForValue().set(cacheKey, "sent", DEFAULT_CACHE_TTL);
        log.debug("알림 캐시 저장: {}", cacheKey);
    }

    public String buildCacheKey(Long userId, NotificationType type, String contextKey) {
        return String.format(CACHE_KEY_FORMAT, userId, type.name(), contextKey);
    }

    public boolean hasRecentNotification(Long userId, NotificationType type, String contextKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildCacheKey(userId, type, contextKey)));
    }

    public void clearNotificationCache(Long userId, NotificationType type, String contextKey) {
        String cacheKey = buildCacheKey(userId, type, contextKey);
        redisTemplate.delete(cacheKey);
        log.debug("알림 캐시 삭제: {}", cacheKey);
    }

    // KEYS 대신 SCAN 사용 - 운영 Redis 블로킹 방지
    public void clearAllUserNotificationCache(Long userId) {
        String pattern = String.format("NOTIFY:%d:*", userId);
        Set<String> keys = scanKeys(pattern);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("사용자 알림 캐시 전체 삭제: userId={}, keys={}", userId, keys.size());
    }

    // KEYS 대신 SCAN 사용 - 운영 Redis 블로킹 방지
    public Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        } catch (Exception e) {
            log.error("Redis SCAN 실패: pattern={}, error={}", pattern, e.getMessage());
        }
        return keys;
    }
}
