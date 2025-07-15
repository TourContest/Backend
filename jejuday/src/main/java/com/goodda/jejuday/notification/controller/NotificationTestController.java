package com.goodda.jejuday.notification.controller;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.service.AttendanceReminderScheduler;
import com.goodda.jejuday.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/v1/test-notification")
@RequiredArgsConstructor
@Tag(name = "알림 테스트 API", description = "FCM 알림 테스트용 API입니다.")
public class NotificationTestController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final AttendanceReminderScheduler attendanceReminderScheduler;

    @PostMapping("/challenge")
    @Operation(summary = "챌린지 장소 도달 알림 테스트")
    public String testChallenge(
            @Parameter(description = "유저 ID") @RequestParam Long userId,
            @Parameter(description = "챌린지 장소 ID") @RequestParam Long placeId) {
        User user = getUser(userId);
        notificationService.sendChallengeNotification(user, "📍 챌린지 장소 도달!", placeId, user.getFcmToken());
        return "챌린지 알림 발송됨";
    }

    @PostMapping("/comment")
    @Operation(summary = "댓글에 대댓글 알림 테스트")
    public String testCommentReply(
            @Parameter(description = "유저 ID") @RequestParam Long userId,
            @Parameter(description = "댓글 ID") @RequestParam Long commentId) {
        User user = getUser(userId);
        notificationService.notifyCommentReply(user, commentId, "💬 누군가 당신의 댓글에 답글을 남겼어요!");
        return "대댓글 알림 발송됨";
    }

    @PostMapping("/reply")
    @Operation(summary = "게시글에 댓글 알림 테스트")
    public String testPostReply(
            @Parameter(description = "유저 ID") @RequestParam Long userId,
            @Parameter(description = "게시글 ID") @RequestParam Long postId) {
        User user = getUser(userId);
        notificationService.sendReplyNotification(user, "📝 게시글에 댓글이 달렸어요!", postId, user.getFcmToken());
        return "댓글 알림 발송됨";
    }

    @PostMapping("/step")
    @Operation(summary = "걸음수 목표 달성 알림 테스트")
    public String testStep(@Parameter(description = "유저 ID") @RequestParam Long userId) {
        User user = getUser(userId);
        notificationService.sendStepNotification(user, "🚶 오늘 목표 걸음수 달성!", user.getFcmToken());
        return "걸음수 알림 발송됨";
    }

    @PostMapping("/like")
    @Operation(summary = "좋아요 누적 알림 테스트")
    public String testLike(
            @Parameter(description = "유저 ID") @RequestParam Long userId,
            @Parameter(description = "게시글 ID") @RequestParam Long postId,
            @Parameter(description = "좋아요 수") @RequestParam int likeCount) {
        User user = getUser(userId);
        notificationService.notifyLikeMilestone(user, likeCount, postId);
        return "좋아요 알림 발송됨";
    }

    @PostMapping("/popularity")
    @Operation(summary = "인기글 TOP10 진입 알림 테스트")
    public String testPopularity(
            @Parameter(description = "유저 ID") @RequestParam Long userId,
            @Parameter(description = "게시글 ID") @RequestParam Long postId,
            @Parameter(description = "좋아요 수") @RequestParam int likeCount) {
        User user = getUser(userId);
        notificationService.checkAndNotifyPopularPostByLike(user, postId, likeCount, LocalDateTime.now());
        return "인기글 알림 발송됨";
    }

    @PostMapping("/attendance")
    @Operation(summary = "출석 리마인드 알림 수동 트리거")
    public ResponseEntity<String> triggerAttendanceReminder() {
        attendanceReminderScheduler.sendAttendanceReminders();
        return ResponseEntity.ok("출석 리마인드 알림 전송 완료");
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저"));
    }
}
