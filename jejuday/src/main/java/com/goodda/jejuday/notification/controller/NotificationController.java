package com.goodda.jejuday.notification.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.security.CustomUserDetails;
import com.goodda.jejuday.auth.service.UserService;
import com.goodda.jejuday.notification.dto.FcmTokenUpdateRequest;
import com.goodda.jejuday.notification.dto.NotificationDto;
import com.goodda.jejuday.notification.dto.NotificationSettingRequest;
import com.goodda.jejuday.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notification")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;
    private final UserRepository userRepository;

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            String email = userDetails.getUsername();
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
        }
        throw new IllegalArgumentException("User is not authenticated");
    }

    @GetMapping
    @Operation(summary = "내 알림 목록 조회", description = "사용자 본인의 알림 리스트를 가져옵니다.")
    public List<NotificationDto> getMyNotifications() {
        User user = getAuthenticatedUser();
        return notificationService.getNotifications(user);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "알림 읽음 처리", description = "개별 알림을 읽음 상태로 변경합니다.")
    public void markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
    }

    @PostMapping("/fcm-token")
    @Operation(summary = "FCM 토큰 등록/수정", description = "로그인 후 클라이언트에서 전달된 FCM 토큰을 저장합니다.")
    public ApiResponse<?> updateFcmToken(@RequestBody FcmTokenUpdateRequest request) {
        User user = getAuthenticatedUser();
        userService.updateFcmToken(user.getId(), request.getFcmToken());
        return ApiResponse.onSuccess("FCM 토큰 업데이트 성공");
    }

    @PostMapping("/notification-setting")
    @Operation(summary = "알림 설정 변경", description = "푸시 알림 수신 여부를 설정합니다.")
    public ResponseEntity<ApiResponse<String>> updateNotificationSetting(
            @RequestBody NotificationSettingRequest request) {
        User user = getAuthenticatedUser();
        userService.updateNotificationSetting(user.getId(), request.isEnabled());
        return ResponseEntity.ok(ApiResponse.onSuccess("알림 설정이 변경되었습니다."));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "단일 알림 삭제")
    public ResponseEntity<String> deleteNotification(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long notificationId) {

        User user = getUser(userDetails.getUserId());
        notificationService.deleteOne(user, notificationId);

        return ResponseEntity.ok("알림이 삭제되었습니다.");
    }

    @DeleteMapping("/all")
    @Operation(summary = "전체 알림 삭제")
    public ResponseEntity<String> deleteAllNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {

        User user = getUser(userDetails.getUserId());
        notificationService.deleteAll(user);

        return ResponseEntity.ok("전체 알림이 삭제되었습니다.");
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }
}
