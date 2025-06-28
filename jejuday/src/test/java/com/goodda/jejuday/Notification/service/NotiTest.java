package com.goodda.jejuday.Notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Notification.entity.NotificationEntity;
import com.goodda.jejuday.Notification.repository.NotificationRepository;
import com.google.api.core.ApiFutures;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class NotiTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private User user;

    @BeforeEach
    void setup() {
        user = User.builder()
                .id(1L)
                .fcmToken("test-token")
                .isNotificationEnabled(true)
                .nickname("tester")
                .build();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void sendChallengeNotification_shouldSend() throws Exception {
        // given
        String cacheKey = "NOTIFY:1:CHALLENGE:challenge-place:10";
        when(redisTemplate.hasKey(cacheKey)).thenReturn(false);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFuture("success"));

        // when
        notificationService.sendChallengeNotification(user, "챌린지 도달!", 10L, user.getFcmToken());

        // then
        verify(notificationRepository).save(any(NotificationEntity.class));
        verify(firebaseMessaging).sendAsync(any(Message.class));
        verify(valueOperations).set(eq(cacheKey), eq("sent"), any());
    }

    @Test
    void sendStepNotification_shouldSkipIfDisabled() {
        // given
        user.setNotificationEnabled(false);

        // when
        notificationService.sendStepNotification(user, "5000걸음!", user.getFcmToken());

        // then
        verify(notificationRepository, never()).save(any());
        verify(firebaseMessaging, never()).sendAsync(any());
        verify(redisTemplate, never()).hasKey(any()); // 조건이 false니까 Redis도 안 씀
    }


    @Test
    void notifyLikeMilestone_shouldTriggerAt50() {
        String cacheKey = "NOTIFY:1:LIKE:like:123:1";
        when(redisTemplate.hasKey(cacheKey)).thenReturn(false);

        when(redisTemplate.hasKey(cacheKey)).thenReturn(false);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFuture("success"));

        notificationService.notifyLikeMilestone(user, 50, 123L);

        verify(notificationRepository).save(any());
        verify(firebaseMessaging).sendAsync(any());
    }

    @Test
    void checkAndNotifyPopularPostByLike_shouldNotifyIfTop10() throws Exception {
        // given
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRank("community:ranking", "community:321")).thenReturn(5L);
        when(firebaseMessaging.sendAsync(any())).thenReturn(ApiFutures.immediateFuture("success"));

        // when
        notificationService.checkAndNotifyPopularPostByLike(user, 321L, 100, LocalDateTime.now());

        // then
        verify(notificationRepository).save(any());
        verify(firebaseMessaging).sendAsync(any());
    }
}
