package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.dto.NotificationDto;
import com.goodda.jejuday.notification.entity.NotificationEntity;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.notification.service.Impl.NotificationServiceImpl;
import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationServiceImpl {

    private final NotificationRepository notificationRepository;
    private final FirebaseMessaging firebaseMessaging;
    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationValidator notificationValidator;
    private final NotificationCacheManager cacheManager;

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMillis(50);

    public void sendNotificationInternal(User user, String message, NotificationType type,
                                         String contextKey, String token) {
        log.info("=== 알림 전송 시작 ===");
        log.info("사용자: {}, 타입: {}, 메시지: {}", user.getId(), type, message);

        // 1. 알림 허용 여부 검증
        if (!notificationValidator.isNotificationAllowed(user, type, contextKey)) {
            log.warn("알림 전송 차단: 사용자={}, 타입={}, 컨텍스트={}", user.getId(), type, contextKey);
            return;
        }

        // 2. FCM 토큰 유효성 검증
        if (!isValidToken(token)) {
            log.warn("FCM 토큰이 유효하지 않음: 사용자={}", user.getId());
            // 토큰이 없어도 DB에는 저장 (내부 알림용)
        }

        try {
            // 3. DB에 알림 저장 (동기)
            NotificationEntity savedNotification = saveNotificationSync(user, message, type, token);
            log.info("알림 DB 저장 성공: ID={}, 타입={}", savedNotification.getId(), type);

            // 4. FCM 전송 (비동기) - 토큰이 유효한 경우에만
            if (isValidToken(token)) {
                sendFcmNotificationAsync(token, message);
            }

            // 5. 캐시에 전송 기록 저장 (중복 방지용)
            cacheManager.markNotificationAsSent(user.getId(), type, contextKey);

            log.info("=== 알림 전송 완료 ===");
        } catch (Exception e) {
            log.error("알림 전송 실패: 사용자={}, 에러={}", user.getId(), e.getMessage(), e);
            // 예외를 다시 던지지 않고 로깅만 (다른 서비스에 영향 주지 않도록)
        }
    }

    @Transactional
    public NotificationEntity saveNotificationSync(User user, String message,
                                                   NotificationType type, String token) {
        try {
            NotificationEntity notification = createNotificationEntity(user, message, type, token);
            NotificationEntity saved = notificationRepository.save(notification);
            notificationRepository.flush(); // 즉시 DB에 반영
            log.debug("알림 저장 완료: ID={}, 사용자={}, 타입={}", saved.getId(), user.getId(), type);
            return saved;
        } catch (Exception e) {
            log.error("알림 DB 저장 실패: 사용자={}, 타입={}, 에러={}", user.getId(), type, e.getMessage());
            throw e;
        }
    }

    private NotificationEntity createNotificationEntity(User user, String message,
                                                        NotificationType type, String token) {
        return NotificationEntity.builder()
                .user(user)
                .message(message)
                .isRead(false)
                .type(type)
                .createdAt(LocalDateTime.now())
                .targetToken(token)
                .build();
    }

    private CompletableFuture<Void> sendFcmNotificationAsync(String token, String message) {
        return CompletableFuture.runAsync(() -> {
            try {
                Message fcmMessage = createFcmMessage(token, message);
                ApiFuture<String> response = firebaseMessaging.sendAsync(fcmMessage);

                // 타임아웃 설정으로 무한 대기 방지
                String result = response.get(10, TimeUnit.SECONDS);
                log.info("FCM 전송 성공: 토큰={}, 결과={}", maskToken(token), result);

            } catch (Exception e) {
                log.error("FCM 전송 실패: 토큰={}, 에러={}", maskToken(token), e.getMessage());
                // FCM 실패 시 토큰 무효화 처리 (옵션)
                handleFcmFailure(token, e);
            }
        });
    }

    private void handleFcmFailure(String token, Exception e) {
        // FCM 에러 코드에 따른 처리
        String errorMessage = e.getMessage();
        if (errorMessage != null) {
            if (errorMessage.contains("registration-token-not-registered") ||
                    errorMessage.contains("invalid-registration-token")) {
                log.warn("유효하지 않은 FCM 토큰 감지: {}", maskToken(token));
                // 여기서 토큰 무효화 처리 가능
            }
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "invalid";
        }
        return token.substring(0, 10) + "***";
    }

    private boolean isValidToken(String token) {
        return token != null && !token.trim().isEmpty() && token.length() > 20;
    }

    private Message createFcmMessage(String token, String messageBody) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle("제주데이") // 대괄호 제거
                        .setBody(messageBody)
                        .build())
                .build();
    }

    @Override
    public void sendChallengeNotification(User user, String message, Long challengePlaceId, String token) {
        log.info("챌린지 알림 전송: 사용자={}, 장소={}, 메시지={}", user.getId(), challengePlaceId, message);
        String contextKey = "challenge-place:" + challengePlaceId;
        sendNotificationInternal(user, message, NotificationType.CHALLENGE, contextKey, token);
    }

    @Override
    public void sendReplyNotification(User user, String message, Long postId, String token) {
        log.info("댓글 알림 전송: 사용자={}, 게시글={}, 메시지={}", user.getId(), postId, message);
        String contextKey = "post:" + postId + ":reply";
        sendNotificationInternal(user, message, NotificationType.REPLY, contextKey, token);
    }

    @Override
    public void sendStepNotification(User user, String message, String token) {
        log.info("걸음수 알림 전송: 사용자={}, 메시지={}", user.getId(), message);
        String contextKey = "step-goal:" + LocalDate.now();
        sendNotificationInternal(user, message, NotificationType.STEP, contextKey, token);
    }

    @Override
    public void notifyCommentReply(User user, Long commentId, String message) {
        log.info("대댓글 알림 전송: 사용자={}, 댓글={}, 메시지={}", user.getId(), commentId, message);
        String contextKey = "comment:" + commentId;
        sendNotificationInternal(user, message, NotificationType.COMMENTS, contextKey, user.getFcmToken());
    }

    /**
     * 좋아요 마일스톤 알림 (50개 단위)
     */
    @Override
    public void notifyLikeMilestone(User user, int likeCount, Long postId) {
        if (!isLikeMilestone(likeCount)) {
            log.debug("좋아요 마일스톤 아님: 좋아요수={}", likeCount);
            return;
        }

        String message = String.format("게시글이 좋아요 %,d개를 달성했어요!", likeCount);
        String contextKey = "like:" + postId + ":" + (likeCount / 50);
        sendNotificationInternal(user, message, NotificationType.LIKE, contextKey, user.getFcmToken());
    }

    private boolean isLikeMilestone(int likeCount) {
        return likeCount > 0 && likeCount % 50 == 0;
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        try {
            NotificationEntity notification = findNotificationById(notificationId);
            notification.setRead(true);
            log.debug("알림 읽음 처리: ID={}", notificationId);
        } catch (Exception e) {
            log.error("알림 읽음 처리 실패: ID={}, 에러={}", notificationId, e.getMessage());
            throw e;
        }
    }

    private NotificationEntity findNotificationById(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 알림이 존재하지 않습니다: " + notificationId));
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(User user) {
        try {
            List<NotificationEntity> notifications = notificationRepository.findAllByUserOrderByCreatedAtDesc(user);
            log.debug("알림 조회 완료: 사용자={}, 알림수={}", user.getId(), notifications.size());

            return notifications.stream()
                    .map(this::convertToDto)
                    .toList();
        } catch (Exception e) {
            log.error("알림 조회 실패: 사용자={}, 에러={}", user.getId(), e.getMessage());
            throw e;
        }
    }

    private NotificationDto convertToDto(NotificationEntity notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .message(notification.getMessage())
                .type(notification.getType())
                .createdAt(notification.getCreatedAt())
                .isRead(notification.isRead())
                .nickname(notification.getUser().getNickname())
                .build();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(User user) {
        try {
            long count = notificationRepository.countByUserAndIsRead(user, false);
            log.debug("읽지 않은 알림 수: 사용자={}, 개수={}", user.getId(), count);
            return count;
        } catch (Exception e) {
            log.error("읽지 않은 알림 수 조회 실패: 사용자={}, 에러={}", user.getId(), e.getMessage());
            return 0;
        }
    }

    @Transactional
    public int markAllAsRead(User user) {
        try {
            List<NotificationEntity> unreadNotifications = notificationRepository
                    .findByUserAndIsRead(user, false);

            unreadNotifications.forEach(notification -> notification.setRead(true));
            notificationRepository.saveAll(unreadNotifications);

            log.info("전체 알림 읽음 처리: 사용자={}, 처리수={}", user.getId(), unreadNotifications.size());
            return unreadNotifications.size();
        } catch (Exception e) {
            log.error("전체 알림 읽음 처리 실패: 사용자={}, 에러={}", user.getId(), e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void deleteOne(User user, Long notificationId) {
        try {
            notificationRepository.deleteByIdAndUser(notificationId, user);
            log.info("알림 삭제 완료: 사용자={}, 알림ID={}", user.getId(), notificationId);
        } catch (Exception e) {
            log.error("알림 삭제 실패: 사용자={}, 알림ID={}, 에러={}", user.getId(), notificationId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void deleteAll(User user) {
        try {
            notificationRepository.deleteAllByUser(user);
            log.info("전체 알림 삭제 완료: 사용자={}", user.getId());
        } catch (Exception e) {
            log.error("전체 알림 삭제 실패: 사용자={}, 에러={}", user.getId(), e.getMessage());
            throw e;
        }
    }
}