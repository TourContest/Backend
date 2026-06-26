package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.dto.NotificationRequest;
import com.goodda.jejuday.notification.entity.NotificationType;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 알림 유형별 {@link NotificationRequest}를 생성하는 팩토리.
 *
 * <p>Open/Closed 원칙: 새 알림 유형 추가 시 이 클래스에 팩토리 메서드만 추가하면 되며,
 * {@link NotificationService#send}는 수정하지 않아도 된다.
 */
public final class NotificationFactory {

    private NotificationFactory() {}

    public static NotificationRequest challenge(User user, String message, Long placeId) {
        return new NotificationRequest(user, message, NotificationType.CHALLENGE,
                "challenge-place:" + placeId);
    }

    public static NotificationRequest reply(User user, String message, Long postId) {
        return new NotificationRequest(user, message, NotificationType.REPLY,
                "post:" + postId + ":reply");
    }

    public static NotificationRequest step(User user, String message) {
        return new NotificationRequest(user, message, NotificationType.STEP,
                "step-goal:" + LocalDate.now());
    }

    public static NotificationRequest commentReply(User user, Long commentId, String message) {
        return new NotificationRequest(user, message, NotificationType.COMMENTS,
                "comment:" + commentId);
    }

    /**
     * 좋아요 마일스톤 알림. 50의 배수가 아니면 {@link Optional#empty()}.
     * 비즈니스 조건을 팩토리가 보유해 호출 측의 if 문 반복을 없앤다.
     */
    public static Optional<NotificationRequest> likeMilestone(User user, int likeCount, Long postId) {
        if (likeCount <= 0 || likeCount % 50 != 0) return Optional.empty();
        String message = String.format("게시글이 좋아요 %,d개를 달성했어요!", likeCount);
        return Optional.of(new NotificationRequest(user, message, NotificationType.LIKE,
                "like:" + postId + ":" + (likeCount / 50)));
    }

    public static NotificationRequest promotion(User user, String message,
                                                 NotificationType type, String contextKey) {
        return new NotificationRequest(user, message, type, contextKey);
    }
}
