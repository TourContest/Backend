package com.goodda.jejuday.notification.service;

import static com.goodda.jejuday.notification.util.NotificationConstants.attendanceContextKey;
import static com.goodda.jejuday.notification.util.NotificationConstants.challengeContextKey;
import static com.goodda.jejuday.notification.util.NotificationConstants.commentContextKey;
import static com.goodda.jejuday.notification.util.NotificationConstants.likeMilestoneContextKey;
import static com.goodda.jejuday.notification.util.NotificationConstants.replyContextKey;
import static com.goodda.jejuday.notification.util.NotificationConstants.stepContextKey;

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

    private static final int LIKE_MILESTONE_INTERVAL = 50;

    private final NotificationCommandService commandService;
    private final NotificationQueryService queryService;

    // ─── 발송 ──────────────────────────────────────────────────

    @Override
    public void sendChallengeNotification(User user, String message, Long challengePlaceId, String token) {
        send(user, message, NotificationType.CHALLENGE, challengeContextKey(challengePlaceId), token);
    }

    @Override
    public void sendReplyNotification(User user, String message, Long postId, String token) {
        send(user, message, NotificationType.REPLY, replyContextKey(postId), token);
    }

    @Override
    public void sendStepNotification(User user, String message, String token) {
        send(user, message, NotificationType.STEP, stepContextKey(), token);
    }

    @Override
    public void notifyCommentReply(User user, Long commentId, String message) {
        send(user, message, NotificationType.COMMENTS, commentContextKey(commentId), user.getFcmToken());
    }

    @Override
    public void notifyLikeMilestone(User user, int likeCount, Long postId) {
        if (!isLikeMilestone(likeCount)) return;
        int milestone = likeCount / LIKE_MILESTONE_INTERVAL;
        send(user,
                String.format("게시글이 좋아요 %,d개를 달성했어요!", likeCount),
                NotificationType.LIKE,
                likeMilestoneContextKey(postId, milestone),
                user.getFcmToken());
    }

    public void sendNotificationInternal(User user, String message, NotificationType type,
                                         String contextKey, String token) {
        send(user, message, type, contextKey, token);
    }

    // ─── 조회 ──────────────────────────────────────────────────

    public Page<NotificationDto> getNotifications(User user, Pageable pageable) {
        return queryService.getNotifications(user, pageable);
    }

    public long getUnreadCount(User user) {
        return queryService.getUnreadCount(user);
    }

    // ─── 상태 변경 ─────────────────────────────────────────────

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

    private void send(User user, String message, NotificationType type, String contextKey, String token) {
        commandService.send(NotificationCommand.builder()
                .user(user).message(message).type(type)
                .contextKey(contextKey).token(token)
                .build());
    }

    private boolean isLikeMilestone(int likeCount) {
        return likeCount > 0 && likeCount % LIKE_MILESTONE_INTERVAL == 0;
    }
}
