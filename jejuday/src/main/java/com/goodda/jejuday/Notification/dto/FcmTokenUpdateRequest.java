package com.goodda.jejuday.Notification.dto;

import lombok.Data;

@Data
public class FcmTokenUpdateRequest {
    private Long userId;
    private String fcmToken;
}