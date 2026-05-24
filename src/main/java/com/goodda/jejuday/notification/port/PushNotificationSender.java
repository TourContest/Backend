package com.goodda.jejuday.notification.port;

public interface PushNotificationSender {

    void send(String token, String title, String body);

    boolean isAvailable();
}
