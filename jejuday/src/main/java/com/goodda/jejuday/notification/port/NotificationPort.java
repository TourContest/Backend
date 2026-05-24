package com.goodda.jejuday.notification.port;

import com.goodda.jejuday.auth.entity.User;

/**
 * 외부 도메인(스팟, 출석, 댓글 등)에서 알림을 발송할 때 사용하는 진입 포트.
 * DIP 적용: 외부 서비스가 구현체(NotificationService)가 아닌 이 인터페이스에 의존하도록 한다.
 */
public interface NotificationPort {

    void sendChallengeNotification(User user, String message, Long challengePlaceId, String token);

    void sendReplyNotification(User user, String message, Long postId, String token);

    void sendStepNotification(User user, String message, String token);

    void notifyCommentReply(User user, Long commentId, String message);

    void notifyLikeMilestone(User user, int likeCount, Long postId);
}
