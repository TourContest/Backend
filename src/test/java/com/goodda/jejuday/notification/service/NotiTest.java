package com.goodda.jejuday.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.dto.NotificationCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotiTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationCommandService commandService;

    @Mock
    private NotificationQueryService queryService;

    private User user;

    @BeforeEach
    void setup() {
        user = User.builder()
                .id(1L)
                .fcmToken("test-token")
                .isNotificationEnabled(true)
                .nickname("tester")
                .build();
    }

    @Test
    void sendChallengeNotification_delegates_to_commandService() {
        notificationService.sendChallengeNotification(user, "챌린지 도달!", 10L, user.getFcmToken());
        verify(commandService).send(any(NotificationCommand.class));
    }

    @Test
    void sendStepNotification_delegates_to_commandService() {
        notificationService.sendStepNotification(user, "5000걸음!", user.getFcmToken());
        verify(commandService).send(any(NotificationCommand.class));
    }

    @Test
    void notifyLikeMilestone_sendsAtMilestone() {
        notificationService.notifyLikeMilestone(user, 50, 123L);
        verify(commandService).send(any(NotificationCommand.class));
    }

    @Test
    void notifyLikeMilestone_skipsBeforeMilestone() {
        notificationService.notifyLikeMilestone(user, 49, 123L);
        verify(commandService, never()).send(any());
    }
}
