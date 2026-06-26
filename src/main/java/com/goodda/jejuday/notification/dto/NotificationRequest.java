package com.goodda.jejuday.notification.dto;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.entity.NotificationType;

/**
 * 알림 전송 요청 값 객체 (불변).
 * {@link com.goodda.jejuday.notification.service.NotificationFactory}로 생성한다.
 */
public record NotificationRequest(
        User user,
        String message,
        NotificationType type,
        String contextKey
) {}
