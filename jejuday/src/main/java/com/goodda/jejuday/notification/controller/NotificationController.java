package com.goodda.jejuday.notification.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.security.CustomUserDetails;
import com.goodda.jejuday.auth.service.UserService;
import com.goodda.jejuday.notification.dto.FcmTokenUpdateRequest;
import com.goodda.jejuday.notification.dto.NotificationDto;
import com.goodda.jejuday.notification.dto.NotificationSettingRequest;
import com.goodda.jejuday.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/notification")
@RequiredArgsConstructor
@Validated
@Tag(name = "알림 관리 API", description = "사용자 알림 관리 및 설정 API")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "내 알림 목록 조회 (페이지)", description = "인증된 사용자의 알림 목록을 최신순으로 페이지 단위로 조회합니다.")
    public ResponseEntity<ApiResponse<Page<NotificationDto>>> getMyNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        User user = getUser(userDetails);
        Page<NotificationDto> notifications = notificationService.getNotifications(user, pageable);
        log.debug("알림 목록 조회: userId={}, total={}", user.getId(), notifications.getTotalElements());
        return ResponseEntity.ok(ApiResponse.onSuccess(notifications));
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다.")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable @NotNull @Positive Long notificationId) {

        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(ApiResponse.onSuccessVoid("알림이 읽음 처리되었습니다."));
    }

    @PostMapping("/mark-all-read")
    @Operation(summary = "전체 알림 읽음 처리", description = "사용자의 모든 알림을 읽음 상태로 변경합니다.")
    public ResponseEntity<ApiResponse<String>> markAllAsRead(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {

        User user = getUser(userDetails);
        int count = notificationService.markAllAsRead(user);
        return ResponseEntity.ok(ApiResponse.onSuccess(count + "개의 알림이 읽음 처리되었습니다."));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "단일 알림 삭제", description = "특정 알림을 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable @NotNull @Positive Long notificationId) {

        User user = getUser(userDetails);
        notificationService.deleteOne(user, notificationId);
        return ResponseEntity.ok(ApiResponse.onSuccessVoid("알림이 삭제되었습니다."));
    }

    @DeleteMapping("/all")
    @Operation(summary = "전체 알림 삭제", description = "해당 사용자의 모든 알림을 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> deleteAllNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {

        User user = getUser(userDetails);
        notificationService.deleteAll(user);
        return ResponseEntity.ok(ApiResponse.onSuccessVoid("전체 알림이 삭제되었습니다."));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "읽지 않은 알림 수 조회")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {

        User user = getUser(userDetails);
        return ResponseEntity.ok(ApiResponse.onSuccess(notificationService.getUnreadCount(user)));
    }

    @PostMapping("/fcm-token")
    @Operation(summary = "FCM 토큰 등록/수정", description = "클라이언트에서 생성된 FCM 토큰을 서버에 등록합니다.")
    public ResponseEntity<ApiResponse<Void>> updateFcmToken(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody FcmTokenUpdateRequest request) {

        userService.updateFcmToken(userDetails.getUserId(), request.getFcmToken());
        return ResponseEntity.ok(ApiResponse.onSuccessVoid("FCM 토큰이 등록되었습니다."));
    }

    @PostMapping("/settings")
    @Operation(summary = "알림 설정 변경", description = "푸시 알림 수신 여부를 설정합니다.")
    public ResponseEntity<ApiResponse<Void>> updateNotificationSetting(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody NotificationSettingRequest request) {

        userService.updateNotificationSetting(userDetails.getUserId(), request.isEnabled());
        String status = request.isEnabled() ? "활성화" : "비활성화";
        return ResponseEntity.ok(ApiResponse.onSuccessVoid("알림이 " + status + "되었습니다."));
    }

    @GetMapping("/settings")
    @Operation(summary = "현재 알림 설정 조회")
    public ResponseEntity<ApiResponse<Boolean>> getNotificationSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {

        User user = getUser(userDetails);
        return ResponseEntity.ok(ApiResponse.onSuccess(user.isNotificationEnabled()));
    }

    private User getUser(CustomUserDetails userDetails) {
        return userService.getUserById(userDetails.getUserId());
    }
}
