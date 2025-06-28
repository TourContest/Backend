package com.goodda.jejuday.Notification.port;

import com.goodda.jejuday.Auth.entity.User;
import java.time.LocalDateTime;

public interface NotificationPort {

    //  챌린지 장소 도달
    void sendChallengeNotification(User user, String message, Long challengePlaceId, String token);

    //  커뮤니티 댓글
    void sendReplyNotification(User user, String message, Long postId, String token);

    //  목표 걸음수 도달
    void sendStepNotification(User user, String message, String token);

    //  커뮤니티 대댓글
    void notifyCommentReply(User user, Long commentId, String message);

    //  좋아요 수 기준 알림
    void notifyLikeMilestone(User user, int likeCount, Long postId);

    //  인기글 진입
    void checkAndNotifyPopularPostByLike(User user, Long postId, int likeCount, LocalDateTime createdAt);
}
