package com.goodda.jejuday.notification.service;

import static com.goodda.jejuday.notification.util.NotificationConstants.CACHE_KEY_FORMAT;
import static com.goodda.jejuday.notification.util.NotificationConstants.DEFAULT_CACHE_TTL;

import com.goodda.jejuday.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCacheManager {

    private final RedisTemplate<String, String> redisTemplate;


    public void markNotificationAsSent(Long userId, NotificationType type, String contextKey) {
        String cacheKey = buildCacheKey(userId, type, contextKey);
        redisTemplate.opsForValue().set(cacheKey, "sent", DEFAULT_CACHE_TTL);

        log.debug("알림 전송 캐시 저장: {}", cacheKey);
    }

    public String buildCacheKey(Long userId, NotificationType type, String contextKey) {
        return String.format(CACHE_KEY_FORMAT, userId, type.name(), contextKey);
    }

    public boolean hasRecentNotification(Long userId, NotificationType type, String contextKey) {
        String cacheKey = buildCacheKey(userId, type, contextKey);
        return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
    }

    public void clearNotificationCache(Long userId, NotificationType type, String contextKey) {
        String cacheKey = buildCacheKey(userId, type, contextKey);
        redisTemplate.delete(cacheKey);

        log.debug("알림 캐시 삭제: {}", cacheKey);
    }

    public void clearAllUserNotificationCache(Long userId) {
        String pattern = String.format("NOTIFY:%d:*", userId);

        redisTemplate.delete(redisTemplate.keys(pattern));

        log.info("사용자 모든 알림 캐시 삭제: 사용자={}", userId);
    }
}