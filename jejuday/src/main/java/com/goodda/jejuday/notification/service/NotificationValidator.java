package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationValidator {

    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationCacheManager cacheManager;

    public boolean isNotificationAllowed(User user, NotificationType type, String contextKey) {
        if (!user.isNotificationEnabled()) {
            log.debug("알림 비활성화된 사용자: {}", user.getId());
            return false;
        }

        if (isDuplicateNotification(user.getId(), type, contextKey)) {
            log.debug("중복 알림 차단: 사용자={}, 타입={}, 컨텍스트={}",
                    user.getId(), type, contextKey);
            return false;
        }

        if (isReplyNotificationBlocked(user, type, contextKey)) {
            log.debug("챌린지 알림으로 인한 댓글 알림 차단: 사용자={}, 컨텍스트={}",
                    user.getId(), contextKey);
            return false;
        }

        return true;
    }

    private boolean isDuplicateNotification(Long userId, NotificationType type, String contextKey) {
        String cacheKey = cacheManager.buildCacheKey(userId, type, contextKey);
        return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
    }

    private boolean isReplyNotificationBlocked(User user, NotificationType type, String contextKey) {
        if (type != NotificationType.REPLY) {
            return false;
        }

        return hasChallengeNotification(user, contextKey);
    }

    private boolean hasChallengeNotification(User user, String contextKey) {
        String challengeKey = cacheManager.buildCacheKey(
                user.getId(),
                NotificationType.CHALLENGE,
                extractPrefix(contextKey)
        );
        return Boolean.TRUE.equals(redisTemplate.hasKey(challengeKey));
    }

    private String extractPrefix(String contextKey) {
        String[] parts = contextKey.split(":");
        return parts.length > 1 ? parts[0] + ":" + parts[1] : contextKey;
    }
}