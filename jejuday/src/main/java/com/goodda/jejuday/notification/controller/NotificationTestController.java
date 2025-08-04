package com.goodda.jejuday.notification.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.service.UserService;
import com.goodda.jejuday.notification.service.AttendanceReminderScheduler;
import com.goodda.jejuday.notification.service.NotificationService;
import com.goodda.jejuday.notification.service.SpotPromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/test-notification")
@RequiredArgsConstructor
@Tag(name = "알림 테스트 API", description = "FCM 알림 테스트용 API입니다.")
public class NotificationTestController {

    private final NotificationService notificationService;
    private final UserService userService;
    private final AttendanceReminderScheduler attendanceReminderScheduler;
    private final SpotPromotionService spotPromotionService;

    @PostMapping("/challenge")
    @Operation(summary = "챌린지 장소 도달 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testChallenge(
            @Parameter(description = "유저 ID") @RequestParam Long userId,
            @Parameter(description = "챌린지 장소 ID") @RequestParam Long placeId) {
        User user = userService.getUserById(userId);
        notificationService.sendChallengeNotification(user, "📍 챌린지 장소 도달!", placeId, user.getFcmToken());
        return ResponseEntity.ok(ApiResponse.onSuccess("챌린지 알림 발송됨"));
    }

    @PostMapping("/comment")
    @Operation(summary = "댓글에 대댓글 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testCommentReply(
            @Parameter(description = "유저 ID") @RequestParam Long userId,
            @Parameter(description = "댓글 ID") @RequestParam Long commentId) {
        User user = userService.getUserById(userId);
        notificationService.notifyCommentReply(user, commentId, "💬 누군가 당신의 댓글에 답글을 남겼어요!");
        return ResponseEntity.ok(ApiResponse.onSuccess("대댓글 알림 발송됨"));
    }

    @PostMapping("/reply")
    @Operation(summary = "게시글에 댓글 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testPostReply(
            @Parameter(description = "유저 ID") @RequestParam Long userId,
            @Parameter(description = "게시글 ID") @RequestParam Long postId) {
        User user = userService.getUserById(userId);
        notificationService.sendReplyNotification(user, "📝 게시글에 댓글이 달렸어요!", postId, user.getFcmToken());
        return ResponseEntity.ok(ApiResponse.onSuccess("댓글 알림 발송됨"));
    }

    @PostMapping("/step")
    @Operation(summary = "걸음수 목표 달성 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testStep(@Parameter(description = "유저 ID") @RequestParam Long userId) {
        User user = userService.getUserById(userId);
        notificationService.sendStepNotification(user, "🚶 오늘 목표 걸음수 달성!", user.getFcmToken());
        return ResponseEntity.ok(ApiResponse.onSuccess("걸음수 알림 발송됨"));
    }

    @PostMapping("/like")
    @Operation(summary = "좋아요 누적 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testLike(
            @Parameter(description = "유저 ID") @RequestParam Long userId,
            @Parameter(description = "게시글 ID") @RequestParam Long postId,
            @Parameter(description = "좋아요 수") @RequestParam int likeCount) {
        User user = userService.getUserById(userId);
        notificationService.notifyLikeMilestone(user, likeCount, postId);
        return ResponseEntity.ok(ApiResponse.onSuccess("좋아요 알림 발송됨"));
    }

    @PostMapping("/spot-promotion")
    @Operation(summary = "레딧 기반 스팟 승격 수동 실행", description = "레딧 알고리즘 기반 점수 계산 및 Spot/Challenge 승격을 수동 실행합니다.")
    public ResponseEntity<ApiResponse<String>> triggerSpotPromotion() {
        spotPromotionService.promoteSpotsPeriodically();
        return ResponseEntity.ok(ApiResponse.onSuccess("스팟 승격 계산이 실행되었습니다."));
    }

    @PostMapping("/attendance")
    @Operation(summary = "출석 리마인드 알림 수동 트리거")
    public ResponseEntity<ApiResponse<String>> triggerAttendanceReminder() {
        attendanceReminderScheduler.sendAttendanceReminders();
        return ResponseEntity.ok(ApiResponse.onSuccess("출석 리마인드 알림 전송 완료"));
    }
}
