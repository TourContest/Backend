package com.goodda.jejuday.Notification.controller;

import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Auth.repository.UserRepository;
import com.goodda.jejuday.Notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/v1/test-notification")
@RequiredArgsConstructor
public class NotificationTestController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @PostMapping("/challenge")
    public String testChallenge(@RequestParam Long userId, @RequestParam Long placeId) {
        User user = getUser(userId);
        notificationService.sendChallengeNotification(user, "📍 챌린지 장소 도달!", placeId, user.getFcmToken());
        return "챌린지 알림 발송됨";
    }

    @PostMapping("/comment")
    public String testCommentReply(@RequestParam Long userId, @RequestParam Long commentId) {
        User user = getUser(userId);
        notificationService.notifyCommentReply(user, commentId, "💬 누군가 당신의 댓글에 답글을 남겼어요!");
        return "대댓글 알림 발송됨";
    }

    @PostMapping("/reply")
    public String testPostReply(@RequestParam Long userId, @RequestParam Long postId) {
        User user = getUser(userId);
        notificationService.sendReplyNotification(user, "📝 게시글에 댓글이 달렸어요!", postId, user.getFcmToken());
        return "댓글 알림 발송됨";
    }

    @PostMapping("/step")
    public String testStep(@RequestParam Long userId) {
        User user = getUser(userId);
        notificationService.sendStepNotification(user, "🚶 오늘 목표 걸음수 달성!", user.getFcmToken());
        return "걸음수 알림 발송됨";
    }

    @PostMapping("/like")
    public String testLike(@RequestParam Long userId, @RequestParam Long postId, @RequestParam int likeCount) {
        User user = getUser(userId);
        notificationService.notifyLikeMilestone(user, likeCount, postId);
        return "좋아요 알림 발송됨";
    }

    @PostMapping("/popularity")
    public String testPopularity(@RequestParam Long userId, @RequestParam Long postId, @RequestParam int likeCount) {
        User user = getUser(userId);
        notificationService.checkAndNotifyPopularPostByLike(user, postId, likeCount, LocalDateTime.now());
        return "인기글 알림 발송됨";
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저"));
    }
}
