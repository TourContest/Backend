package com.goodda.jejuday.notification.controller;

import com.goodda.jejuday.attendance.repository.ReminderTarget;
import com.goodda.jejuday.attendance.repository.UserAttendanceRepository;
import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.service.UserService;
import com.goodda.jejuday.notification.entity.NotificationEntity;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.notification.service.AttendanceReminderScheduler;
import com.goodda.jejuday.notification.service.NotificationFactory;
import com.goodda.jejuday.notification.service.NotificationService;
import com.goodda.jejuday.notification.service.SpotPromotionService;
import com.goodda.jejuday.notification.service.SpotScoreCalculator;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.service.SpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/test-notification")
@RequiredArgsConstructor
@Profile({"dev", "local", "test"})
@Tag(name = "알림 테스트 API", description = "FCM 알림 및 승격 시스템 테스트용 API (개발/테스트 환경 전용)")
public class NotificationTestController {

    private final EntityManagerFactory entityManagerFactory;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final UserAttendanceRepository attendanceRepository;
    private final UserService userService;
    private final AttendanceReminderScheduler attendanceReminderScheduler;
    private final SpotPromotionService spotPromotionService;
    private final SpotScoreCalculator spotScoreCalculator;
    private final SpotService spotService;
    private final NotificationRepository notificationRepository;

    @PostMapping("/challenge")
    @Operation(summary = "챌린지 장소 도달 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testChallenge(
            @RequestParam @NotNull @Positive Long userId,
            @RequestParam @NotNull @Positive Long placeId) {
        try {
            User user = userService.getUserById(userId);
            notificationService.send(NotificationFactory.challenge(user, "챌린지 장소 도달! 테스트 알림입니다.", placeId));
            return ResponseEntity.ok(ApiResponse.onSuccess("챌린지 알림이 발송되었습니다."));
        } catch (Exception e) {
            log.error("챌린지 알림 테스트 실패: userId={}", userId, e);
            return ResponseEntity.badRequest().body(ApiResponse.onFailure("TEST_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/comment")
    @Operation(summary = "댓글에 대댓글 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testCommentReply(
            @RequestParam @NotNull @Positive Long userId,
            @RequestParam @NotNull @Positive Long commentId) {
        try {
            User user = userService.getUserById(userId);
            notificationService.send(NotificationFactory.commentReply(user, commentId, "누군가 당신의 댓글에 답글을 남겼어요! (테스트)"));
            return ResponseEntity.ok(ApiResponse.onSuccess("대댓글 알림이 발송되었습니다."));
        } catch (Exception e) {
            log.error("대댓글 알림 테스트 실패: userId={}", userId, e);
            return ResponseEntity.badRequest().body(ApiResponse.onFailure("TEST_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/reply")
    @Operation(summary = "게시글에 댓글 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testPostReply(
            @RequestParam @NotNull @Positive Long userId,
            @RequestParam @NotNull @Positive Long postId) {
        try {
            User user = userService.getUserById(userId);
            notificationService.send(NotificationFactory.reply(user, "게시글에 댓글이 달렸어요! (테스트)", postId));
            return ResponseEntity.ok(ApiResponse.onSuccess("댓글 알림이 발송되었습니다."));
        } catch (Exception e) {
            log.error("댓글 알림 테스트 실패: userId={}", userId, e);
            return ResponseEntity.badRequest().body(ApiResponse.onFailure("TEST_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/step")
    @Operation(summary = "걸음수 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testStep(
            @RequestParam @NotNull @Positive Long userId,
            @RequestParam(defaultValue = "20000") @Min(0) @Max(100000) int steps) {
        try {
            User user = userService.getUserById(userId);
            if (steps >= 20000) {
                String message = String.format("오늘 목표 2만보 달성! 현재 %,d보를 걸었어요! 대단해요!", steps);
                notificationService.send(NotificationFactory.step(user, message));
            } else if (steps >= 10000) {
                String message = String.format("1만보 달성! 현재 %,d보, 목표까지 %,d보 남았어요! 파이팅!", steps, 20000 - steps);
                notificationService.send(NotificationFactory.step(user, message));
            } else {
                return ResponseEntity.ok(ApiResponse.onSuccess(String.format("1만보 미달 (%,d보) - 알림 전송 안 함", steps)));
            }
            return ResponseEntity.ok(ApiResponse.onSuccess(String.format("걸음수 알림이 발송되었습니다. (현재: %,d보)", steps)));
        } catch (Exception e) {
            log.error("걸음수 알림 테스트 실패: userId={}", userId, e);
            return ResponseEntity.badRequest().body(ApiResponse.onFailure("TEST_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/like")
    @Operation(summary = "좋아요 누적 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testLike(
            @RequestParam @NotNull @Positive Long userId,
            @RequestParam @NotNull @Positive Long postId,
            @RequestParam @NotNull @Min(50) @Max(10000) int likeCount) {
        try {
            User user = userService.getUserById(userId);
            NotificationFactory.likeMilestone(user, likeCount, postId)
                    .ifPresent(notificationService::send);
            return ResponseEntity.ok(ApiResponse.onSuccess(String.format("좋아요 %d개 달성 알림이 발송되었습니다.", likeCount)));
        } catch (Exception e) {
            log.error("좋아요 알림 테스트 실패: userId={}", userId, e);
            return ResponseEntity.badRequest().body(ApiResponse.onFailure("TEST_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/send-all-types/{userId}")
    @Operation(summary = "모든 타입 알림 테스트 전송")
    public ResponseEntity<ApiResponse<String>> sendAllTypeNotifications(@PathVariable Long userId) {
        try {
            User user = userService.getUserById(userId);
            notificationService.send(NotificationFactory.challenge(user, "챌린지 테스트 알림", 1L));
            notificationService.send(NotificationFactory.reply(user, "댓글 테스트 알림", 1L));
            notificationService.send(NotificationFactory.step(user, "걸음수 테스트 알림"));
            notificationService.send(NotificationFactory.commentReply(user, 1L, "대댓글 테스트 알림"));
            NotificationFactory.likeMilestone(user, 50, 1L).ifPresent(notificationService::send);
            return ResponseEntity.ok(ApiResponse.onSuccess("모든 타입 알림 전송 완료"));
        } catch (Exception e) {
            log.error("전체 알림 테스트 실패", e);
            return ResponseEntity.badRequest().body(ApiResponse.onFailure("ALL_TYPE_TEST_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/spot-promotion")
    @Operation(summary = "스팟 승격 수동 실행")
    public ResponseEntity<ApiResponse<String>> triggerSpotPromotion() {
        try {
            spotPromotionService.promoteSpotsPeriodically();
            return ResponseEntity.ok(ApiResponse.onSuccess("스팟 승격 프로세스가 실행되었습니다."));
        } catch (Exception e) {
            log.error("스팟 승격 수동 실행 실패", e);
            return ResponseEntity.internalServerError().body(ApiResponse.onFailure("PROMOTION_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/attendance")
    @Operation(summary = "출석 리마인드 알림 수동 트리거")
    public ResponseEntity<ApiResponse<String>> triggerAttendanceReminder() {
        try {
            attendanceReminderScheduler.sendAttendanceReminders();
            return ResponseEntity.ok(ApiResponse.onSuccess("출석 리마인드 알림이 전송되었습니다."));
        } catch (Exception e) {
            log.error("출석 리마인더 수동 실행 실패", e);
            return ResponseEntity.internalServerError().body(ApiResponse.onFailure("REMINDER_FAILED", e.getMessage()));
        }
    }

    @GetMapping("/spot-score/{spotId}")
    @Operation(summary = "스팟 점수 조회")
    public ResponseEntity<ApiResponse<Double>> getSpotScore(@PathVariable @NotNull @Positive Long spotId) {
        try {
            Spot spot = spotService.getSpotById(spotId);
            double score = spotScoreCalculator.calculateScore(spot);
            return ResponseEntity.ok(ApiResponse.onSuccess(score));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.onFailure("SCORE_FETCH_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/clear-cache/{spotId}")
    @Operation(summary = "스팟 캐시 삭제")
    public ResponseEntity<ApiResponse<String>> clearSpotCache(@PathVariable @NotNull @Positive Long spotId) {
        try {
            spotScoreCalculator.invalidateScoreCache(spotId);
            return ResponseEntity.ok(ApiResponse.onSuccess(String.format("스팟 %d의 캐시가 삭제되었습니다.", spotId)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.onFailure("CACHE_CLEAR_FAILED", e.getMessage()));
        }
    }

    @GetMapping("/debug/notifications/{userId}")
    @Operation(summary = "사용자 알림 디버깅")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debugUserNotifications(@PathVariable Long userId) {
        try {
            User user = userService.getUserById(userId);
            Map<String, Object> debug = new HashMap<>();
            debug.put("userId", user.getId());
            debug.put("notificationEnabled", user.isNotificationEnabled());
            debug.put("fcmToken", user.getFcmToken() != null ? "존재" : "없음");

            List<NotificationEntity> notifications = notificationRepository.findAllByUserOrderByCreatedAtDesc(user);
            debug.put("totalNotifications", notifications.size());
            debug.put("notificationsByType", notifications.stream()
                    .collect(Collectors.groupingBy(NotificationEntity::getType, Collectors.counting())));
            debug.put("recentNotifications", notifications.stream()
                    .limit(5)
                    .map(n -> Map.of("id", n.getId(), "type", n.getType(),
                            "message", n.getMessage(), "createdAt", n.getCreatedAt(), "isRead", n.isRead()))
                    .toList());

            return ResponseEntity.ok(ApiResponse.onSuccess(debug));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.onFailure("DEBUG_FAILED", e.getMessage()));
        }
    }

    // --- 개선 전/후 비교 테스트 ---

    @GetMapping("/query-stats")
    @Operation(summary = "출석 캐시 상태 조회 (현재 Set 기반)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQueryStats() {
        Map<String, Object> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        String setKey = String.format(AttendanceReminderScheduler.ATTENDANCE_SET_KEY, today);
        Set<String> members = redisTemplate.opsForSet().members(setKey);
        result.put("redis_출석_Set_키", setKey);
        result.put("오늘_출석자수", members != null ? members.size() : 0);
        return ResponseEntity.ok(ApiResponse.onSuccess(result));
    }

    @PostMapping("/attendance/before")
    @Operation(summary = "출석 리마인더 - 개선 전 (N+1 쿼리 시뮬레이션)")
    public ResponseEntity<ApiResponse<String>> attendanceBefore() {
        resetHibernateStats();
        long start = System.currentTimeMillis();
        LocalDate today = LocalDate.now();

        List<User> users = userRepository.findAll().stream()
                .filter(User::isNotificationEnabled)
                .filter(u -> u.getFcmToken() != null && !u.getFcmToken().isBlank())
                .collect(Collectors.toList());

        int sent = (int) users.stream()
                .filter(u -> attendanceRepository.findByUserIdAndCheckDate(u.getId(), today).isEmpty())
                .count();

        return ResponseEntity.ok(ApiResponse.onSuccess(String.format(
                "개선 전: 대상=%d, 발송대상=%d, DB쿼리=%d회, 처리시간=%dms",
                users.size(), sent, getQueryCount(), System.currentTimeMillis() - start)));
    }

    @PostMapping("/attendance/after")
    @Operation(summary = "출석 리마인더 - 개선 후 (Redis Set 일괄 조회)")
    public ResponseEntity<ApiResponse<String>> attendanceAfter() {
        resetHibernateStats();
        long start = System.currentTimeMillis();
        LocalDate today = LocalDate.now();

        List<ReminderTarget> targets = attendanceRepository.findAttendanceReminderTargets();
        String setKey = String.format(AttendanceReminderScheduler.ATTENDANCE_SET_KEY, today);
        Set<String> members = redisTemplate.opsForSet().members(setKey);
        Set<Long> attendedIds = members == null ? Set.of() :
                members.stream().map(Long::parseLong).collect(Collectors.toSet());

        int sent = (int) targets.stream().filter(t -> !attendedIds.contains(t.getId())).count();

        return ResponseEntity.ok(ApiResponse.onSuccess(String.format(
                "개선 후: 대상=%d, 출석=%d, 발송대상=%d, DB쿼리=%d회, 처리시간=%dms",
                targets.size(), attendedIds.size(), sent, getQueryCount(), System.currentTimeMillis() - start)));
    }

    @PostMapping("/attendance/seed-cache")
    @Operation(summary = "출석 캐시 사전 세팅 - Set 기반 (테스트용)")
    public ResponseEntity<ApiResponse<String>> seedAttendanceCache(
            @RequestParam(defaultValue = "500") int count) {
        LocalDate today = LocalDate.now();
        String setKey = String.format(AttendanceReminderScheduler.ATTENDANCE_SET_KEY, today);
        String[] ids = new String[count];
        for (int i = 1; i <= count; i++) ids[i - 1] = String.valueOf(i);
        redisTemplate.opsForSet().add(setKey, ids);
        return ResponseEntity.ok(ApiResponse.onSuccess(String.format("%d명 출석 Set 세팅 완료", count)));
    }

    @PostMapping("/simulate-promotion/{spotId}")
    @Operation(summary = "승격 시뮬레이션 (실제 승격 없음)")
    public ResponseEntity<ApiResponse<String>> simulatePromotion(@PathVariable @NotNull @Positive Long spotId) {
        try {
            Spot spot = spotService.getSpotById(spotId);
            double score = spotScoreCalculator.calculateScore(spot);
            String result = String.format("스팟 ID=%d, 타입=%s, 점수=%.2f, 조회수=%d\n%s",
                    spot.getId(), spot.getType(), score, spot.getViewCount(),
                    spot.getType() == Spot.SpotType.POST
                            ? "POST→SPOT: 필요=10.0, 가능=" + (score >= 10.0 ? "예" : "아니오")
                            : "SPOT→CHALLENGE: 상위 30% 기준 (개별 평가 필요)");
            return ResponseEntity.ok(ApiResponse.onSuccess(result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.onFailure("SIMULATION_FAILED", e.getMessage()));
        }
    }

    private void resetHibernateStats() {
        entityManagerFactory.unwrap(SessionFactory.class).getStatistics().clear();
    }

    private long getQueryCount() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics().getPrepareStatementCount();
    }
}
