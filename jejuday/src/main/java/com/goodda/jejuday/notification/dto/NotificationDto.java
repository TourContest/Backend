package com.goodda.jejuday.notification.dto;


import com.goodda.jejuday.notification.model.NotificationType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationDto {
    private Long id;
    private String message;
    private NotificationType type;
    private LocalDateTime createdAt;
    private boolean isRead;
    private String nickname;
}

