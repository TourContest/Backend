package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.notification.exception.FcmSendException;
import com.goodda.jejuday.notification.port.PushNotificationSender;
import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FcmNotificationSender implements PushNotificationSender {

    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    @Override
    @Retryable(
            retryFor = FcmSendException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void send(String token, String title, String body) {
        if (firebaseMessaging == null) {
            log.warn("Firebase not initialized, skipping FCM push");
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            ApiFuture<String> future = firebaseMessaging.sendAsync(message);
            String result = future.get(10, TimeUnit.SECONDS);
            log.debug("FCM sent: token={}, messageId={}", maskToken(token), result);

        } catch (Exception e) {
            handleError(token, e);
            throw new FcmSendException("FCM 전송 실패: " + e.getMessage(), e);
        }
    }

    @Recover
    public void recover(FcmSendException ex, String token, String title, String body) {
        log.error("FCM 전송 3회 모두 실패: token={}, error={}", maskToken(token), ex.getMessage());
    }

    @Override
    public boolean isAvailable() {
        return firebaseMessaging != null;
    }

    private void handleError(String token, Exception e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("registration-token-not-registered")
                || msg.contains("invalid-registration-token"))) {
            log.warn("만료된 FCM 토큰 감지: {}", maskToken(token));
        } else {
            log.error("FCM 전송 오류: token={}, error={}", maskToken(token), msg);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) return "invalid";
        return token.substring(0, 10) + "***";
    }
}
