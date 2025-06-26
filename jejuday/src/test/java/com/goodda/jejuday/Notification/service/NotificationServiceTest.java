package com.goodda.jejuday.Notification.service;

import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Notification.entity.NotificationEntity;
import com.goodda.jejuday.Notification.repository.NotificationRepository;
import com.google.api.core.ApiFutures;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private User user;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        user = User.builder()
                .id(1L)
                .fcmToken("test-token")
                .isNotificationEnabled(true)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void sendStepNotification_success_async() throws Exception {
        // given
        String message = "5000걸음 달성!";
        String contextKey = "step:" + LocalDate.now();
        String cacheKey = "NOTIFY:1:STEP:" + contextKey;

        when(redisTemplate.hasKey(cacheKey)).thenReturn(false);
        when(firebaseMessaging.sendAsync(any(Message.class)))
                .thenReturn(ApiFutures.immediateFuture("messageId"));

        // when
        notificationService.sendStepNotification(user, message, user.getFcmToken());

        // then
        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
        verify(firebaseMessaging, times(1)).sendAsync(any(Message.class));
        verify(valueOperations, times(1)).set(eq(cacheKey), eq("sent"), any());
    }

    @Test
    void sendStepNotification_skipped_dueToNotificationDisabled() {
        // given
        user.setNotificationEnabled(false);

        // when
        notificationService.sendStepNotification(user, "알림 꺼짐", user.getFcmToken());

        // then
        verify(firebaseMessaging, never()).sendAsync(any());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendReplyNotification_skipped_dueToChallengeCache() {
        // given
        String challengeKey = "NOTIFY:1:CHALLENGE:challenge:42";
        when(redisTemplate.hasKey(challengeKey)).thenReturn(true);

        // when
        notificationService.sendReplyNotification(user, "댓글", 42L, user.getFcmToken());

        // then
        verify(firebaseMessaging, never()).sendAsync(any());
        verify(notificationRepository, never()).save(any());
    }
}
