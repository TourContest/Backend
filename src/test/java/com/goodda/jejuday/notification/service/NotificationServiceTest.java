package com.goodda.jejuday.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.dto.NotificationCommand;
import com.goodda.jejuday.notification.entity.NotificationEntity;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.notification.exception.NotificationNotFoundException;
import com.goodda.jejuday.notification.port.PushNotificationSender;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationCommandService sut;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private NotificationValidator validator;

    @Mock
    private NotificationCacheManager cacheManager;

    @Mock
    private PushNotificationSender pushSender;

    private User user;

    @BeforeEach
    void setup() {
        user = User.builder()
                .id(1L)
                .fcmToken("test-token")
                .isNotificationEnabled(true)
                .build();
    }

    @Test
    void send_shouldSaveNotification_whenAllowed() {
        NotificationCommand command = NotificationCommand.builder()
                .user(user)
                .message("목표 걸음수 도달")
                .type(NotificationType.STEP)
                .contextKey("step-goal")
                .token("test-token")
                .build();
        given(validator.isNotificationAllowed(user, NotificationType.STEP, "step-goal")).willReturn(true);
        given(notificationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        sut.send(command);

        then(notificationRepository).should().save(any(NotificationEntity.class));
        then(cacheManager).should().markNotificationAsSent(user.getId(), NotificationType.STEP, "step-goal");
    }

    @Test
    void send_shouldSkip_whenValidatorBlocks() {
        NotificationCommand command = NotificationCommand.builder()
                .user(user)
                .message("걸음수")
                .type(NotificationType.STEP)
                .contextKey("step-goal")
                .token("test-token")
                .build();
        given(validator.isNotificationAllowed(any(), any(), any())).willReturn(false);

        sut.send(command);

        then(notificationRepository).should(never()).save(any());
    }

    @Test
    void markAsRead_shouldSetEntityRead() {
        NotificationEntity entity = NotificationEntity.builder().id(1L).isRead(false).build();
        given(notificationRepository.findById(1L)).willReturn(Optional.of(entity));

        sut.markAsRead(1L);

        assertThat(entity.isRead()).isTrue();
    }

    @Test
    void markAsRead_shouldThrow_whenNotFound() {
        given(notificationRepository.findById(99L)).willReturn(Optional.empty());
        assertThrows(NotificationNotFoundException.class, () -> sut.markAsRead(99L));
    }
}
