package com.goodda.jejuday.notification.dto;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationCommand {

    private final User user;
    private final String message;
    private final NotificationType type;
    private final String contextKey;
    private final String token;
}
