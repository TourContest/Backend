package com.goodda.jejuday.notification.dto;

import lombok.Data;

@Data
public class FcmTokenUpdateRequest {
    private Long userId;
    private String fcmToken;
}