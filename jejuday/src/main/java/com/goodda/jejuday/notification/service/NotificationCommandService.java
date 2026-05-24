package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.dto.NotificationCommand;
import com.goodda.jejuday.notification.entity.NotificationEntity;
import com.goodda.jejuday.notification.entity.NotificationOutbox;
import com.goodda.jejuday.notification.exception.NotificationNotFoundException;
import com.goodda.jejuday.notification.port.PushNotificationSender;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationValidator validator;
    private final NotificationCacheManager cacheManager;
    private final PushNotificationSender pushSender;

    @Transactional
    public void send(NotificationCommand command) {
        if (!validator.isNotificationAllowed(command.getUser(), command.getType(), command.getContextKey())) {
            log.debug("알림 전송 차단: userId={}, type={}", command.getUser().getId(), command.getType());
            return;
        }

        // 사용자가 즉시 볼 수 있는 알림 저장
        notificationRepository.save(NotificationEntity.builder()
                .user(command.getUser())
                .message(command.getMessage())
                .type(command.getType())
                .isRead(false)
                .targetToken(command.getToken())
                .build());

        // FCM 전송은 Outbox 패턴으로 신뢰성 보장
        if (isValidToken(command.getToken())) {
            NotificationOutbox outbox = outboxRepository.save(NotificationOutbox.builder()
                    .userId(command.getUser().getId())
                    .message(command.getMessage())
                    .type(command.getType())
                    .contextKey(command.getContextKey())
                    .targetToken(command.getToken())
                    .build());

            sendFcmAsync(outbox);
        }

        cacheManager.markNotificationAsSent(command.getUser().getId(), command.getType(), command.getContextKey());
        log.info("알림 저장 완료: userId={}, type={}", command.getUser().getId(), command.getType());
    }

    @Async
    public void sendFcmAsync(NotificationOutbox outbox) {
        outbox.markProcessing();
        try {
            pushSender.send(outbox.getTargetToken(), "제주데이", outbox.getMessage());
            outbox.markDone();
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.warn("FCM 즉시 전송 실패, 재시도 큐에 등록: outboxId={}", outbox.getId());
            outbox.markFailed(e.getMessage());
            outboxRepository.save(outbox);
        }
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        notification.setRead(true);
        log.debug("알림 읽음 처리: id={}", notificationId);
    }

    @Transactional
    public int markAllAsRead(User user) {
        int count = notificationRepository.markAllAsReadByUser(user);
        log.info("전체 읽음 처리: userId={}, count={}", user.getId(), count);
        return count;
    }

    @Transactional
    public void deleteOne(User user, Long notificationId) {
        notificationRepository.deleteByIdAndUser(notificationId, user);
        log.info("알림 삭제: userId={}, notificationId={}", user.getId(), notificationId);
    }

    @Transactional
    public void deleteAll(User user) {
        notificationRepository.deleteAllByUser(user);
        log.info("전체 알림 삭제: userId={}", user.getId());
    }

    private boolean isValidToken(String token) {
        return token != null && !token.isBlank() && token.length() > 20;
    }
}
