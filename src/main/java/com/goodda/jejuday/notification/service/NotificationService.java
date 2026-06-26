package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.attendance.repository.ReminderTarget;
import com.goodda.jejuday.notification.dto.NotificationDto;
import com.goodda.jejuday.notification.dto.NotificationRequest;
import com.goodda.jejuday.notification.entity.NotificationEntity;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.notification.service.OutboxBulkInserter.BulkOutboxRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    // fcmToken 유효성 최소 길이 — UserAttendanceRepository JPQL의 LENGTH 조건과 동일해야 한다
    static final int FCM_TOKEN_MIN_LENGTH = 20;

    private static final String FCM_TITLE = "제주데이";

    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final OutboxBulkInserter bulkInserter;

    /**
     * 단일 알림 전송 진입점.
     * 알림함(NotificationEntity) 저장 + outbox INSERT를 같은 트랜잭션에서 처리.
     * 실제 FCM 전송은 OutboxPoller가 담당한다.
     */
    @Transactional
    public void send(NotificationRequest request) {
        if (!request.user().isNotificationEnabled()) {
            log.debug("알림 차단 (비활성): userId={}, type={}", request.user().getId(), request.type());
            return;
        }

        NotificationEntity entity = NotificationEntity.builder()
                .user(request.user())
                .message(request.message())
                .type(request.type())
                .isRead(false)
                .targetToken(request.user().getFcmToken())
                .build();
        notificationRepository.save(entity);

        String token = request.user().getFcmToken();
        if (isValidToken(token)) {
            LocalDateTime now = LocalDateTime.now();
            outboxRepository.insertIfNotDuplicate(
                    request.user().getId(), token, FCM_TITLE,
                    request.message(), request.type().name(), request.contextKey(), now);
        }

        log.debug("알림 저장 완료: userId={}, type={}", request.user().getId(), request.type());
    }

    /**
     * 출석 리마인더 배치 outbox INSERT.
     * NotificationEntity는 저장하지 않는다(리마인더는 인앱 알림함 대상이 아님).
     *
     * <p>거대 단일 트랜잭션을 제거하고 청크(500건)당 REQUIRES_NEW 트랜잭션을 사용한다.
     * 부분 실패 시 해당 청크만 롤백되고 다음 실행에서 dedup 키로 재시도가 멱등하게 된다.
     */
    public int scheduleAttendanceReminders(List<ReminderTarget> targets, Set<Long> attendedIds, LocalDate date) {
        String dedupKey = "attendance:" + date;
        String body = "아직 오늘 출석하지 않으셨어요! 한라봉 받으러 오세요";
        String type = NotificationType.ATTENDANCE.name();

        List<BulkOutboxRow> toInsert = targets.stream()
                .filter(t -> !attendedIds.contains(t.getId()) && isValidToken(t.getFcmToken()))
                .map(t -> new BulkOutboxRow(t.getId(), t.getFcmToken(), FCM_TITLE, body, type, dedupKey))
                .toList();

        int total = 0;
        for (int i = 0; i < toInsert.size(); i += OutboxBulkInserter.CHUNK_SIZE) {
            List<BulkOutboxRow> chunk = toInsert.subList(
                    i, Math.min(i + OutboxBulkInserter.CHUNK_SIZE, toInsert.size()));
            total += bulkInserter.insertChunk(chunk);
        }
        return total;
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 알림이 존재하지 않습니다: " + notificationId));
        notification.setRead(true);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(com.goodda.jejuday.auth.entity.User user) {
        return notificationRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(com.goodda.jejuday.auth.entity.User user) {
        return notificationRepository.countByUserAndIsRead(user, false);
    }

    @Transactional
    public int markAllAsRead(com.goodda.jejuday.auth.entity.User user) {
        List<NotificationEntity> unread = notificationRepository.findByUserAndIsRead(user, false);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public void deleteOne(com.goodda.jejuday.auth.entity.User user, Long notificationId) {
        notificationRepository.deleteByIdAndUser(notificationId, user);
    }

    @Transactional
    public void deleteAll(com.goodda.jejuday.auth.entity.User user) {
        notificationRepository.deleteAllByUser(user);
    }

    static boolean isValidToken(String token) {
        return token != null && !token.trim().isEmpty() && token.length() > FCM_TOKEN_MIN_LENGTH;
    }

    private NotificationDto toDto(NotificationEntity notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .message(notification.getMessage())
                .type(notification.getType())
                .createdAt(notification.getCreatedAt())
                .isRead(notification.isRead())
                .nickname(notification.getUser().getNickname())
                .build();
    }
}
