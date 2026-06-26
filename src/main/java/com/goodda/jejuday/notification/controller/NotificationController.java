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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1/notification")
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@Tag(name = "알림 관리 API", description = "사용자 알림 관리 및 설정 API")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "내 알림 목록 조회", description = "인증된 사용자의 알림 리스트를 최신순으로 조회합니다.")
    public ResponseEntity<ApiResponse<List<NotificationDto>>> getMyNotifications() {
        User user = getAuthenticatedUser();
        List<NotificationDto> notifications = notificationService.getNotifications(user);
        log.info("알림 목록 조회 완료: 사용자={}, 알림수={}", user.getId(), notifications.size());
        return ResponseEntity.ok(ApiResponse.onSuccess(notifications));
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 상태로 변경합니다.")
    public ResponseEntity<ApiResponse<String>> markAsRead(
            @Parameter(description = "알림 ID", example = "1")
            @PathVariable @NotNull @Positive Long notificationId) {
        notificationService.markAsRead(notificationId);
        log.info("알림 읽음 처리 완료: 알림ID={}", notificationId);
        return ResponseEntity.ok(ApiResponse.onSuccess("알림이 읽음 처리되었습니다."));
    }

    @PostMapping("/fcm-token")
    @Operation(summary = "FCM 토큰 등록/수정", description = "클라이언트에서 생성된 FCM 토큰을 서버에 등록합니다.")
    public ResponseEntity<ApiResponse<String>> updateFcmToken(
            @Valid @RequestBody FcmTokenUpdateRequest request) {
        User user = getAuthenticatedUser();
        userService.updateFcmToken(user.getId(), request.getFcmToken());
        log.info("FCM 토큰 업데이트 완료: 사용자={}", user.getId());
        return ResponseEntity.ok(ApiResponse.onSuccess("FCM 토큰이 성공적으로 등록되었습니다."));
    }

    @PostMapping("/settings")
    @Operation(summary = "알림 설정 변경", description = "푸시 알림 수신 여부를 설정합니다.")
    public ResponseEntity<ApiResponse<String>> updateNotificationSetting(
            @Valid @RequestBody NotificationSettingRequest request) {
        User user = getAuthenticatedUser();
        userService.updateNotificationSetting(user.getId(), request.isEnabled());
        String status = request.isEnabled() ? "활성화" : "비활성화";
        log.info("알림 설정 변경 완료: 사용자={}, 상태={}", user.getId(), status);
        return ResponseEntity.ok(ApiResponse.onSuccess(String.format("알림이 %s되었습니다.", status)));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "단일 알림 삭제", description = "특정 알림을 삭제합니다.")
    public ResponseEntity<ApiResponse<String>> deleteNotification(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "알림 ID", example = "1")
            @PathVariable @NotNull @Positive Long notificationId) {
        User user = userService.getUserById(userDetails.getUserId());
        notificationService.deleteOne(user, notificationId);
        log.info("알림 삭제 완료: 사용자={}, 알림ID={}", user.getId(), notificationId);
        return ResponseEntity.ok(ApiResponse.onSuccess("알림이 삭제되었습니다."));
    }

    @DeleteMapping("/all")
    @Operation(summary = "전체 알림 삭제", description = "해당 사용자의 모든 알림을 삭제합니다.")
    public ResponseEntity<ApiResponse<String>> deleteAllNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userService.getUserById(userDetails.getUserId());
        notificationService.deleteAll(user);
        log.info("전체 알림 삭제 완료: 사용자={}", user.getId());
        return ResponseEntity.ok(ApiResponse.onSuccess("전체 알림이 삭제되었습니다."));
    }

    @GetMapping("/settings")
    @Operation(summary = "현재 알림 설정 조회", description = "사용자의 현재 알림 설정 상태를 조회합니다.")
    public ResponseEntity<ApiResponse<Boolean>> getNotificationSettings() {
        User user = getAuthenticatedUser();
        boolean isEnabled = user.isNotificationEnabled();
        log.debug("알림 설정 조회: 사용자={}, 상태={}", user.getId(), isEnabled);
        return ResponseEntity.ok(ApiResponse.onSuccess(isEnabled));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "읽지 않은 알림 수 조회", description = "사용자의 읽지 않은 알림 개수를 조회합니다.")
    public ResponseEntity<ApiResponse<Long>> getUnreadNotificationCount() {
        User user = getAuthenticatedUser();
        long unreadCount = notificationService.getUnreadCount(user);
        log.debug("읽지 않은 알림 수 조회: 사용자={}, 개수={}", user.getId(), unreadCount);
        return ResponseEntity.ok(ApiResponse.onSuccess(unreadCount));
    }

    @PostMapping("/mark-all-read")
    @Operation(summary = "전체 알림 읽음 처리", description = "사용자의 모든 알림을 읽음 상태로 변경합니다.")
    public ResponseEntity<ApiResponse<String>> markAllAsRead() {
        User user = getAuthenticatedUser();
        int updatedCount = notificationService.markAllAsRead(user);
        log.info("전체 알림 읽음 처리 완료: 사용자={}, 처리된 알림 수={}", user.getId(), updatedCount);
        return ResponseEntity.ok(ApiResponse.onSuccess(String.format("%d개의 알림이 읽음 처리되었습니다.", updatedCount)));
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            return userService.getUserByEmail(userDetails.getUsername());
        }
        throw new IllegalArgumentException("인증된 사용자가 아닙니다.");
    }
}
