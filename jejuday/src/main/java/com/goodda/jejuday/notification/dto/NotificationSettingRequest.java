package com.goodda.jejuday.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Schema(description = "알림 설정 변경 요청")
public class NotificationSettingRequest {

    @Schema(description = "알림 활성화 여부", example = "true")
    @NotNull(message = "알림 설정 값은 필수입니다.")
    private Boolean enabled;

    // primitive boolean 대신 Boolean 래퍼 클래스 사용으로 validation 개선
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}