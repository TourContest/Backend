package com.goodda.jejuday.Notification.controller;

import com.goodda.jejuday.Auth.dto.ApiResponse;
import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Auth.service.UserService;
import com.goodda.jejuday.Notification.dto.FcmTokenUpdateRequest;
import com.goodda.jejuday.Notification.dto.NotificationDto;
import com.goodda.jejuday.Notification.dto.NotificationSettingRequest;
import com.goodda.jejuday.Notification.service.NotificationService;
import com.goodda.jejuday.common.annotation.certification.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/notification")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "내 알림 목록 조회", description = "사용자 본인의 알림 리스트를 가져옵니다.")
    public List<NotificationDto> getMyNotifications(@CurrentUser User user) {
        return notificationService.getNotifications(user);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "알림 읽음 처리", description = "개별 알림을 읽음 상태로 변경합니다.")
    public void markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
    }

    @PostMapping("/fcm-token")
    @Operation(summary = "FCM 토큰 등록/수정", description = "로그인 후 클라이언트에서 전달된 FCM 토큰을 저장합니다.")
    public ApiResponse<?> updateFcmToken(@CurrentUser User user, @RequestBody FcmTokenUpdateRequest request) {
        userService.updateFcmToken(user.getId(), request.getFcmToken());
        return ApiResponse.onSuccess("FCM 토큰 업데이트 성공");
    }

    @PostMapping("/notification-setting")
    @Operation(summary = "알림 설정 변경", description = "푸시 알림 수신 여부를 설정합니다.")
    public ResponseEntity<ApiResponse<String>> updateNotificationSetting(
            @Parameter(hidden = true) @CurrentUser User user,
            @RequestBody NotificationSettingRequest request
    ) {
        userService.updateNotificationSetting(user.getId(), request.isEnabled());
        return ResponseEntity.ok(ApiResponse.onSuccess("알림 설정이 변경되었습니다."));
    }
}
