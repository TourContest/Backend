package com.goodda.jejuday.Notification.service;

import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Notification.dto.NotificationDto;
import com.goodda.jejuday.Notification.entity.NotificationEntity;
import com.goodda.jejuday.Notification.model.NotificationType;
import com.goodda.jejuday.Notification.repository.NotificationRepository;
import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final FirebaseMessaging firebaseMessaging;
    private final RedisTemplate<String, String> redisTemplate;

    private static final long CACHE_TTL_SECONDS = 30L;

    public void sendChallengeNotification(User user, String message, Long challengeId, String token) {
        String contextKey = "challenge:" + challengeId;
        sendNotificationInternal(user, message, NotificationType.CHALLENGE, contextKey, token);
    }

    public void sendReplyNotification(User user, String message, Long challengeId, String token) {
        String contextKey = "challenge:" + challengeId + ":reply";
        sendNotificationInternal(user, message, NotificationType.REPLY, contextKey, token);
    }

    public void sendStepNotification(User user, String message, String token) {
        String contextKey = "step:" + LocalDate.now();
        sendNotificationInternal(user, message, NotificationType.STEP, contextKey, token);
    }

    private void sendNotificationInternal(User user, String message, NotificationType type, String contextKey,
                                          String token) {
        if (!user.isNotificationEnabled()) {
            return;
        }

        String cacheKey = buildCacheKey(user.getId(), type, contextKey);

        if (type == NotificationType.REPLY && hasChallengeNotification(user, contextKey)) {
            return;
        }
        if (redisTemplate.hasKey(cacheKey)) {
            return;
        }

        NotificationEntity notification = NotificationEntity.builder()
                .user(user)
                .message(message)
                .isRead(false)
                .type(type)
                .createdAt(LocalDateTime.now())
                .targetToken(token)
                .build();
        notificationRepository.save(notification);

        if (token != null && !token.isBlank()) {
            try {
                Message fcmMessage = buildFcmMessage(token, message);
                ApiFuture<String> response = firebaseMessaging.sendAsync(fcmMessage);
                response.addListener(() -> {
                    try {
                        String messageId = response.get();
                        System.out.println("FCM 전송 성공: " + messageId);
                    } catch (Exception e) {
                        System.err.println("FCM 전송 실패: " + e.getMessage());
                    }
                }, Executors.newSingleThreadExecutor());
            } catch (Exception e) {
                throw new RuntimeException("FCM 전송 실패", e);
            }
        }

        redisTemplate.opsForValue().set(cacheKey, "sent", Duration.ofSeconds(CACHE_TTL_SECONDS));
    }

    private boolean hasChallengeNotification(User user, String contextKey) {
        String challengeKey = buildCacheKey(user.getId(), NotificationType.CHALLENGE, extractPrefix(contextKey));
        return Boolean.TRUE.equals(redisTemplate.hasKey(challengeKey));
    }

    private Message buildFcmMessage(String token, String body) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle("[제주데이]")
                        .setBody(body)
                        .build())
                .build();
    }

    private String buildCacheKey(Long userId, NotificationType type, String contextKey) {
        return String.format("NOTIFY:%d:%s:%s", userId, type.name(), contextKey);
    }

    private String extractPrefix(String contextKey) {
        String[] parts = contextKey.split(":");
        return parts.length > 1 ? parts[0] + ":" + parts[1] : contextKey;
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 알림이 존재하지 않습니다."));
        notification.setRead(true);
    }

    public List<NotificationDto> getNotifications(User user) {
        return notificationRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(notification -> NotificationDto.builder()
                        .id(notification.getId())
                        .message(notification.getMessage())
                        .type(notification.getType())
                        .createdAt(notification.getCreatedAt())
                        .isRead(notification.isRead())
                        .nickname(notification.getUser().getNickname())
                        .build())
                .toList();
    }
}
