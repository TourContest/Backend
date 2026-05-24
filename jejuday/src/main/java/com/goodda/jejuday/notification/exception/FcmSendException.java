package com.goodda.jejuday.notification.exception;

public class FcmSendException extends NotificationException {

    public FcmSendException(String message) {
        super("FCM_SEND_FAILED", message);
    }

    public FcmSendException(String message, Throwable cause) {
        super("FCM_SEND_FAILED", message, cause);
    }
}
