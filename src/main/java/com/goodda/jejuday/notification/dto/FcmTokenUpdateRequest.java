package com.goodda.jejuday.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "FCM 토큰 업데이트 요청")
public class FcmTokenUpdateRequest {

    @Schema(description = "FCM 토큰 (Firebase에서 생성)", example = "dJ8Hkf9...")
    @NotBlank(message = "FCM 토큰은 필수입니다.")
    @Size(min = 10, max = 500, message = "FCM 토큰 길이가 올바르지 않습니다.")
    private String fcmToken;
}