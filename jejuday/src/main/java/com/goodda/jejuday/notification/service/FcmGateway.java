package com.goodda.jejuday.notification.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class FcmGateway {

    private final FirebaseMessaging firebaseMessaging;

    // @Nullable: FirebaseMessaging 빈이 없는 환경(로컬/테스트)에서도 기동 가능
    public FcmGateway(@Nullable FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @CircuitBreaker(name = "fcm")
    public BatchResponse sendEach(List<Message> messages) throws FirebaseMessagingException {
        if (firebaseMessaging == null) {
            throw new IllegalStateException("FirebaseMessaging is not initialized");
        }
        log.debug("FCM sendEach 시작: {}건", messages.size());
        return firebaseMessaging.sendEach(messages);
    }
}
