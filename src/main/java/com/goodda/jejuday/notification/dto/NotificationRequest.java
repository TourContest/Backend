package com.goodda.jejuday.notification.dto;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.entity.NotificationType;

/**
 * 알림 전송 요청 값 객체 (불변).
 * {@link com.goodda.jejuday.notification.service.NotificationFactory}로 생성한다.
 * dedupKey 가 있으면 (user, type, dedupKey) 로 알림함/outbox 모두 1건만 남긴다 (걸음수 목표, 승격).
 */
public record NotificationRequest(
        User user,
        String message,
        NotificationType type,
        String contextKey,
        String dedupKey
) {
    /** 멱등 대상이 아닌 일반 알림(dedupKey = null). */
    public NotificationRequest(User user, String message, NotificationType type, String contextKey) {
        this(user, message, type, contextKey, null);
    }
}
