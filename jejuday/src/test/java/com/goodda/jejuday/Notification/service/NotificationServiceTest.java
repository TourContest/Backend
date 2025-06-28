package com.goodda.jejuday.Notification.service;

import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Notification.entity.NotificationEntity;
import com.goodda.jejuday.Notification.model.NotificationType;
import com.goodda.jejuday.Notification.repository.NotificationRepository;
import com.google.api.core.ApiFutures;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
    void sendStepNotification_shouldSendAndSave_whenAllowed() throws Exception {
        // given
        String contextKey = "step-goal:" + LocalDate.now();
        String cacheKey = "NOTIFY:1:STEP:" + contextKey;

        when(redisTemplate.hasKey(cacheKey)).thenReturn(false);
        when(firebaseMessaging.sendAsync(any(Message.class)))
                .thenReturn(ApiFutures.immediateFuture("messageId"));

        // when
        notificationService.sendStepNotification(user, "목표 걸음수 도달", user.getFcmToken());

        // then
        verify(notificationRepository, times(1)).save(any(NotificationEntity.class));
        verify(firebaseMessaging, times(1)).sendAsync(any(Message.class));
        verify(valueOperations, times(1)).set(eq(cacheKey), eq("sent"), any());
    }

    @Test
    void sendStepNotification_shouldSkip_whenUserDisabled() {
        // given
        user.setNotificationEnabled(false);

        // when
        notificationService.sendStepNotification(user, "걸음수", user.getFcmToken());

        // then
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void markAsRead_shouldUpdateNotification() {
        // given
        NotificationEntity entity = NotificationEntity.builder().id(1L).isRead(false).build();
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(entity));

        // when
        notificationService.markAsRead(1L);

        // then
        verify(notificationRepository).findById(1L);
        assert(entity.isRead());
    }
}
