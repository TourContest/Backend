package com.goodda.jejuday.notification.exception;

public class NotificationNotFoundException extends NotificationException {

    public NotificationNotFoundException(Long notificationId) {
        super("NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다: " + notificationId);
    }
}
