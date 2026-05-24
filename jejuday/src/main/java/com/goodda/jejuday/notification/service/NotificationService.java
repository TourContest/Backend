package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.dto.NotificationCommand;
import com.goodda.jejuday.notification.dto.NotificationDto;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.notification.port.NotificationPort;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 알림 기능의 진입점 파사드.
 * 외부 서비스(스팟, 출석, 댓글 등)는 NotificationPort를 통해 알림을 발송한다.
 * 실제 처리는 NotificationCommandService / NotificationQueryService에 위임.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationPort {

    private final NotificationCommandService commandService;
    private final NotificationQueryService queryService;

    // ─── 발송 메서드 ───────────────────────────────────────────

    public void sendChallengeNotification(User user, String message, Long challengePlaceId, String token) {
        commandService.send(NotificationCommand.builder()
                .user(user).message(message).type(NotificationType.CHALLENGE)
                .contextKey("challenge-place:" + challengePlaceId).token(token)
                .build());
    }

    public void sendReplyNotification(User user, String message, Long postId, String token) {
        commandService.send(NotificationCommand.builder()
                .user(user).message(message).type(NotificationType.REPLY)
                .contextKey("post:" + postId + ":reply").token(token)
                .build());
    }

    public void sendStepNotification(User user, String message, String token) {
        commandService.send(NotificationCommand.builder()
                .user(user).message(message).type(NotificationType.STEP)
                .contextKey("step-goal:" + LocalDate.now()).token(token)
                .build());
    }

    public void notifyCommentReply(User user, Long commentId, String message) {
        commandService.send(NotificationCommand.builder()
                .user(user).message(message).type(NotificationType.COMMENTS)
                .contextKey("comment:" + commentId).token(user.getFcmToken())
                .build());
    }

    public void notifyLikeMilestone(User user, int likeCount, Long postId) {
        if (!isLikeMilestone(likeCount)) return;
        commandService.send(NotificationCommand.builder()
                .user(user)
                .message(String.format("게시글이 좋아요 %,d개를 달성했어요!", likeCount))
                .type(NotificationType.LIKE)
                .contextKey("like:" + postId + ":" + (likeCount / 50))
                .token(user.getFcmToken())
                .build());
    }

    // 스케줄러 등 내부 호출용
    public void sendNotificationInternal(User user, String message, NotificationType type,
                                         String contextKey, String token) {
        commandService.send(NotificationCommand.builder()
                .user(user).message(message).type(type)
                .contextKey(contextKey).token(token)
                .build());
    }

    // ─── 조회 메서드 ───────────────────────────────────────────

    public Page<NotificationDto> getNotifications(User user, Pageable pageable) {
        return queryService.getNotifications(user, pageable);
    }

    public long getUnreadCount(User user) {
        return queryService.getUnreadCount(user);
    }

    // ─── 상태 변경 메서드 ──────────────────────────────────────

    public void markAsRead(Long notificationId) {
        commandService.markAsRead(notificationId);
    }

    public int markAllAsRead(User user) {
        return commandService.markAllAsRead(user);
    }

    public void deleteOne(User user, Long notificationId) {
        commandService.deleteOne(user, notificationId);
    }

    public void deleteAll(User user) {
        commandService.deleteAll(user);
    }

    // ─── private ──────────────────────────────────────────────

    private boolean isLikeMilestone(int likeCount) {
        return likeCount > 0 && likeCount % 50 == 0;
    }
}
