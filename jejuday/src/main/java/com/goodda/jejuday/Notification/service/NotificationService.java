package com.goodda.jejuday.Notification.service;

import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Notification.dto.NotificationDto;
import com.goodda.jejuday.Notification.entity.NotificationEntity;
import com.goodda.jejuday.Notification.model.NotificationType;
import com.goodda.jejuday.Notification.port.NotificationPort;
import com.goodda.jejuday.Notification.repository.NotificationRepository;
import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationPort {

    private final NotificationRepository notificationRepository;
    private final FirebaseMessaging firebaseMessaging;
    private final RedisTemplate<String, String> redisTemplate;

    private static final long CACHE_TTL_SECONDS = 5L;
    private static final LocalDateTime BASE_DATE = LocalDateTime.of(2020, 1, 1, 0, 0);

    private void sendNotificationInternal(User user, String message, NotificationType type, String contextKey,
                                          String token) {
        if (!isNotificationAllowed(user, type, contextKey)) {
            return;
        }
        saveNotification(user, message, type, token);
        sendFcmIfTokenValid(token, message);
        markNotificationAsSent(user.getId(), type, contextKey);
    }

    private boolean isNotificationAllowed(User user, NotificationType type, String contextKey) {
        if (!user.isNotificationEnabled()) {
            return false;
        }
        String cacheKey = buildCacheKey(user.getId(), type, contextKey);
        if (type == NotificationType.REPLY && hasChallengeNotification(user, contextKey)) {
            return false;
        }
        return !redisTemplate.hasKey(cacheKey);
    }

    private void saveNotification(User user, String message, NotificationType type, String token) {
        NotificationEntity notification = NotificationEntity.builder()
                .user(user)
                .message(message)
                .isRead(false)
                .type(type)
                .createdAt(LocalDateTime.now())
                .targetToken(token)
                .build();
        notificationRepository.save(notification);
    }

    private void sendFcmIfTokenValid(String token, String message) {
        if (token == null || token.isBlank()) {
            System.out.println("❌ FCM 전송 실패: 토큰이 null이거나 비어있음");
            return;
        }

        try {
            System.out.println("📡 [FCM 전송 시도] 대상 토큰: " + token);
            System.out.println("📨 [FCM 메시지] 내용: " + message);

            Message fcmMessage = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle("[제주데이]")
                            .setBody(message)
                            .build())
                    .build();

            ApiFuture<String> response = firebaseMessaging.sendAsync(fcmMessage);
            response.addListener(() -> {
                try {
                    System.out.println("✅ [FCM 전송 성공] 응답: " + response.get());
                } catch (Exception e) {
                    System.err.println("❌ [FCM 전송 응답 실패]: " + e.getMessage());
                }
            }, Executors.newSingleThreadExecutor());

        } catch (Exception e) {
            System.err.println("🔥 [FCM 예외 발생]: " + e.getMessage());
            throw new RuntimeException("FCM 전송 실패", e);
        }
    }


    private void markNotificationAsSent(Long userId, NotificationType type, String contextKey) {
        String cacheKey = buildCacheKey(userId, type, contextKey);
        redisTemplate.opsForValue().set(cacheKey, "sent", Duration.ofSeconds(CACHE_TTL_SECONDS));
    }

    private String buildCacheKey(Long userId, NotificationType type, String contextKey) {
        return String.format("NOTIFY:%d:%s:%s", userId, type.name(), contextKey);
    }

    private boolean hasChallengeNotification(User user, String contextKey) {
        String challengeKey = buildCacheKey(user.getId(), NotificationType.CHALLENGE, extractPrefix(contextKey));
        return Boolean.TRUE.equals(redisTemplate.hasKey(challengeKey));
    }

    private String extractPrefix(String contextKey) {
        String[] parts = contextKey.split(":");
        return parts.length > 1 ? parts[0] + ":" + parts[1] : contextKey;
    }

    @Override
    public void sendChallengeNotification(User user, String message, Long challengePlaceId, String token) {
        sendNotificationInternal(user, message, NotificationType.CHALLENGE, "challenge-place:" + challengePlaceId,
                token);
    }

    @Override
    public void sendReplyNotification(User user, String message, Long postId, String token) {
        sendNotificationInternal(user, message, NotificationType.REPLY, "post:" + postId + ":reply", token);
    }

    @Override
    public void sendStepNotification(User user, String message, String token) {
        sendNotificationInternal(user, message, NotificationType.STEP, "step-goal:" + LocalDate.now(), token);
    }

    @Override
    public void notifyCommentReply(User user, Long commentId, String message) {
        sendNotificationInternal(user, message, NotificationType.COMMENTS, "comment:" + commentId, user.getFcmToken());
    }

    @Override
    public void notifyLikeMilestone(User user, int likeCount, Long postId) {
        if (likeCount % 50 != 0) {
            return;
        }
        sendNotificationInternal(user, "게시글이 좋아요 " + likeCount + "개를 달성했어요!",
                NotificationType.LIKE, "like:" + postId + ":" + (likeCount / 50), user.getFcmToken());
    }

    @Override
    public void checkAndNotifyPopularPostByLike(User user, Long postId, int likeCount, LocalDateTime createdAt) {
        double score = calculateCompositeScore(likeCount, 0, 0, 0, createdAt);
        redisTemplate.opsForZSet().add("community:ranking", "community:" + postId, score);

        Long rank = redisTemplate.opsForZSet().reverseRank("community:ranking", "community:" + postId);
        if (rank != null && rank < 10) {
            sendNotificationInternal(user, "당신의 게시글이 인기글 TOP10에 진입했어요!",
                    NotificationType.POPULARITY, "popularity:" + postId, user.getFcmToken());
        }
    }

    public void updatePostRanking(Long postId, int likeCount, int commentCount, int viewCount, int certifyCount,
                                  LocalDateTime createdAt, boolean isActive) {
        if (!isActive) {
            return;
        }
        double score = calculateCompositeScore(likeCount, commentCount, viewCount, certifyCount, createdAt);
        redisTemplate.opsForZSet().add("community:ranking", "community:" + postId, score);
    }

    private double calculateCompositeScore(int likeCount, int commentCount, int viewCount, int certifyCount,
                                           LocalDateTime createdAt) {
        int rawScore = (likeCount * 2) + (commentCount * 3) + (viewCount) + (certifyCount * 10);
        double order = Math.log10(Math.max(rawScore, 1));
        long seconds = Duration.between(BASE_DATE, createdAt).getSeconds();
        return order + seconds / 45000.0;
    }

    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void recalculateRanking() {
        // 외부에서 active post 정보를 받아 처리해야 함. 이 메서드는 호출 로직만 유지.
        // 실제 데이터는 다른 서비스에서 주입 받아야 정확히 재계산 가능.
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
