package com.goodda.jejuday.notification.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.service.UserService;
import com.goodda.jejuday.notification.entity.NotificationEntity;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.notification.service.AttendanceReminderScheduler;
import com.goodda.jejuday.notification.service.NotificationService;
import com.goodda.jejuday.notification.service.SpotPromotionService;
import com.goodda.jejuday.notification.service.SpotScoreCalculator;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.service.SpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/test-notification")
@RequiredArgsConstructor
@Tag(name = "알림 테스트 API", description = "FCM 알림 및 승격 시스템 테스트용 API (개발/테스트 환경 전용)")
public class NotificationTestController {

    private final NotificationService notificationService;
    private final UserService userService;
    private final AttendanceReminderScheduler attendanceReminderScheduler;
    private final SpotPromotionService spotPromotionService;
    private final SpotScoreCalculator spotScoreCalculator;
    private final SpotService spotService;
    private final NotificationRepository notificationRepository;

    @PostMapping("/challenge")
    @Operation(summary = "챌린지 장소 도달 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testChallenge(
            @Parameter(description = "유저 ID", example = "1")
            @RequestParam @NotNull @Positive Long userId,
            @Parameter(description = "챌린지 장소 ID", example = "1")
            @RequestParam @NotNull @Positive Long placeId) {
        try {
            User user = userService.getUserById(userId);
            notificationService.sendChallengeNotification(
                    user,
                    "챌린지 장소 도달! 테스트 알림입니다.",
                    placeId,
                    user.getFcmToken()
            );

            log.info("챌린지 알림 테스트 완료: 사용자={}, 장소={}", userId, placeId);
            return ResponseEntity.ok(ApiResponse.onSuccess("챌린지 알림이 발송되었습니다."));
        } catch (Exception e) {
            log.error("챌린지 알림 테스트 실패: 사용자={}, 에러={}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.onFailure("TEST_FAILED", "챌린지 알림 테스트에 실패했습니다."));
        }
    }

    @PostMapping("/comment")
    @Operation(summary = "댓글에 대댓글 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testCommentReply(
            @Parameter(description = "유저 ID", example = "1")
            @RequestParam @NotNull @Positive Long userId,
            @Parameter(description = "댓글 ID", example = "1")
            @RequestParam @NotNull @Positive Long commentId) {
        try {
            User user = userService.getUserById(userId);
            notificationService.notifyCommentReply(
                    user,
                    commentId,
                    "누군가 당신의 댓글에 답글을 남겼어요! (테스트)"
            );

            log.info("대댓글 알림 테스트 완료: 사용자={}, 댓글={}", userId, commentId);
            return ResponseEntity.ok(ApiResponse.onSuccess("대댓글 알림이 발송되었습니다."));
        } catch (Exception e) {
            log.error("대댓글 알림 테스트 실패: 사용자={}, 에러={}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.onFailure("TEST_FAILED", "대댓글 알림 테스트에 실패했습니다."));
        }
    }

    @PostMapping("/reply")
    @Operation(summary = "게시글에 댓글 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testPostReply(
            @Parameter(description = "유저 ID", example = "1")
            @RequestParam @NotNull @Positive Long userId,
            @Parameter(description = "게시글 ID", example = "1")
            @RequestParam @NotNull @Positive Long postId) {
        try {
            User user = userService.getUserById(userId);
            notificationService.sendReplyNotification(
                    user,
                    "게시글에 댓글이 달렸어요! (테스트)",
                    postId,
                    user.getFcmToken()
            );

            log.info("댓글 알림 테스트 완료: 사용자={}, 게시글={}", userId, postId);
            return ResponseEntity.ok(ApiResponse.onSuccess("댓글 알림이 발송되었습니다."));
        } catch (Exception e) {
            log.error("댓글 알림 테스트 실패: 사용자={}, 에러={}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.onFailure("TEST_FAILED", "댓글 알림 테스트에 실패했습니다."));
        }
    }

    @PostMapping("/step")
    @Operation(summary = "걸음수 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testStep(
            @Parameter(description = "유저 ID", example = "1")
            @RequestParam @NotNull @Positive Long userId,
            @Parameter(description = "현재 걸음수", example = "20000")
            @RequestParam(defaultValue = "20000") @Min(0) @Max(100000) int steps) {
        try {
            User user = userService.getUserById(userId);

            if (steps >= 20000) {
                // 2만보 달성 알림
                String message = String.format("오늘 목표 2만보 달성! 현재 %s보를 걸었어요! 대단해요!",
                        String.format("%,d", steps));
                notificationService.sendStepNotification(user, message, user.getFcmToken());
            } else if (steps >= 10000) {
                // 1만보 달성 알림 (목표 미달성 격려)
                String message = String.format("1만보 달성! 현재 %s보, 목표까지 %s보 남았어요! 파이팅!",
                        String.format("%,d", steps),
                        String.format("%,d", 20000 - steps));
                notificationService.sendStepNotification(user, message, user.getFcmToken());
            } else {
                // 1만보 미만은 알림 없음
                return ResponseEntity.ok(ApiResponse.onSuccess(
                        String.format("1만보 미달 (%,d보) - 알림 전송하지 않음", steps)
                ));
            }

            log.info("걸음수 알림 테스트 완료: 사용자={}, 걸음수={}", userId, steps);
            return ResponseEntity.ok(ApiResponse.onSuccess(
                    String.format("걸음수 알림이 발송되었습니다. (현재: %,d보)", steps)
            ));
        } catch (Exception e) {
            log.error("걸음수 알림 테스트 실패: 사용자={}, 에러={}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.onFailure("TEST_FAILED", "걸음수 알림 테스트에 실패했습니다."));
        }
    }

    @PostMapping("/like")
    @Operation(summary = "좋아요 누적 알림 테스트")
    public ResponseEntity<ApiResponse<String>> testLike(
            @Parameter(description = "유저 ID", example = "1")
            @RequestParam @NotNull @Positive Long userId,
            @Parameter(description = "게시글 ID", example = "1")
            @RequestParam @NotNull @Positive Long postId,
            @Parameter(description = "좋아요 수 (50의 배수)", example = "50")
            @RequestParam @NotNull @Min(50) @Max(10000) int likeCount) {
        try {
            User user = userService.getUserById(userId);
            notificationService.notifyLikeMilestone(user, likeCount, postId);

            log.info("좋아요 알림 테스트 완료: 사용자={}, 게시글={}, 좋아요={}", userId, postId, likeCount);
            return ResponseEntity.ok(ApiResponse.onSuccess(
                    String.format("좋아요 %d개 달성 알림이 발송되었습니다.", likeCount)
            ));
        } catch (Exception e) {
            log.error("좋아요 알림 테스트 실패: 사용자={}, 에러={}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.onFailure("TEST_FAILED", "좋아요 알림 테스트에 실패했습니다."));
        }
    }

    @PostMapping("/spot-promotion")
    @Operation(
            summary = "스팟 승격 수동 실행",
            description = "Reddit 알고리즘 기반 점수 계산 및 Spot/Challenge 승격을 수동으로 실행합니다."
    )
    public ResponseEntity<ApiResponse<String>> triggerSpotPromotion() {
        try {
            log.info("스팟 승격 수동 실행 시작");
            spotPromotionService.promoteSpotsPeriodically();

            return ResponseEntity.ok(ApiResponse.onSuccess(
                    "스팟 승격 프로세스가 성공적으로 실행되었습니다. 로그를 확인해주세요."
            ));
        } catch (Exception e) {
            log.error("스팟 승격 수동 실행 실패: 에러={}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.onFailure("PROMOTION_FAILED", "스팟 승격 실행에 실패했습니다."));
        }
    }

    @PostMapping("/attendance")
    @Operation(summary = "출석 리마인드 알림 수동 트리거")
    public ResponseEntity<ApiResponse<String>> triggerAttendanceReminder() {
        try {
            log.info("출석 리마인더 수동 실행 시작");
            attendanceReminderScheduler.sendAttendanceReminders();

            return ResponseEntity.ok(ApiResponse.onSuccess(
                    "출석 리마인드 알림이 성공적으로 전송되었습니다."
            ));
        } catch (Exception e) {
            log.error("출석 리마인더 수동 실행 실패: 에러={}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.onFailure("REMINDER_FAILED", "출석 리마인더 전송에 실패했습니다."));
        }
    }

    @GetMapping("/spot-score/{spotId}")
    @Operation(
            summary = "스팟 점수 조회",
            description = "특정 스팟의 현재 Reddit 알고리즘 점수를 조회합니다."
    )
    public ResponseEntity<ApiResponse<Double>> getSpotScore(
            @Parameter(description = "스팟 ID", example = "1")
            @PathVariable @NotNull @Positive Long spotId) {
        try {
            Spot spot = spotService.getSpotById(spotId);
            double score = spotScoreCalculator.calculateScore(spot);

            log.info("스팟 점수 조회: 스팟={}, 점수={}", spotId, score);
            return ResponseEntity.ok(ApiResponse.onSuccess(score));
        } catch (Exception e) {
            log.error("스팟 점수 조회 실패: 스팟={}, 에러={}", spotId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.onFailure("SCORE_FETCH_FAILED", "스팟 점수 조회에 실패했습니다."));
        }
    }

    @PostMapping("/clear-cache/{spotId}")
    @Operation(
            summary = "스팟 캐시 삭제",
            description = "특정 스팟의 점수 및 통계 캐시를 삭제합니다."
    )
    public ResponseEntity<ApiResponse<String>> clearSpotCache(
            @Parameter(description = "스팟 ID", example = "1")
            @PathVariable @NotNull @Positive Long spotId) {
        try {
            spotScoreCalculator.invalidateScoreCache(spotId);

            log.info("스팟 캐시 삭제 완료: 스팟={}", spotId);
            return ResponseEntity.ok(ApiResponse.onSuccess(
                    String.format("스팟 %d의 캐시가 삭제되었습니다.", spotId)
            ));
        } catch (Exception e) {
            log.error("스팟 캐시 삭제 실패: 스팟={}, 에러={}", spotId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.onFailure("CACHE_CLEAR_FAILED", "캐시 삭제에 실패했습니다."));
        }
    }

    @PostMapping("/simulate-promotion/{spotId}")
    @Operation(
            summary = "승격 시뮬레이션",
            description = "특정 스팟의 승격 가능성을 시뮬레이션합니다 (실제 승격은 하지 않음)."
    )
    public ResponseEntity<ApiResponse<String>> simulatePromotion(
            @Parameter(description = "스팟 ID", example = "1")
            @PathVariable @NotNull @Positive Long spotId) {
        try {
            Spot spot = spotService.getSpotById(spotId);
            double score = spotScoreCalculator.calculateScore(spot);

            String result = analyzePromotionEligibility(spot, score);

            log.info("승격 시뮬레이션 완료: 스팟={}, 점수={}", spotId, score);
            return ResponseEntity.ok(ApiResponse.onSuccess(result));
        } catch (Exception e) {
            log.error("승격 시뮬레이션 실패: 스팟={}, 에러={}", spotId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.onFailure("SIMULATION_FAILED", "승격 시뮬레이션에 실패했습니다."));
        }
    }

    private String analyzePromotionEligibility(Spot spot, double score) {
        StringBuilder result = new StringBuilder();
        result.append(String.format("현재 스팟 정보:\n"));
        result.append(String.format("- ID: %d\n", spot.getId()));
        result.append(String.format("- 타입: %s\n", spot.getType()));
        result.append(String.format("- 점수: %.2f\n", score));
        result.append(String.format("- 조회수: %d\n", spot.getViewCount()));

        if (spot.getType() == Spot.SpotType.POST) {
            result.append(String.format("\n승격 기준 (POST → SPOT):\n"));
            result.append(String.format("- 필요 점수: 10.0\n"));
            result.append(String.format("- 승격 가능: %s\n", score >= 10.0 ? "예" : "아니오"));
        } else if (spot.getType() == Spot.SpotType.SPOT) {
            result.append(String.format("\n승격 기준 (SPOT → CHALLENGE):\n"));
            result.append(String.format("- 기준: 상위 30%%\n"));
            result.append(String.format("- 현재 점수로는 개별 평가 필요\n"));
        }

        return result.toString();
    }

    @GetMapping("/debug/notifications/{userId}")
    @Operation(summary = "사용자 알림 디버깅")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debugUserNotifications(
            @PathVariable Long userId) {
        try {
            User user = userService.getUserById(userId);
            Map<String, Object> debug = new HashMap<>();

            // 사용자 기본 정보
            debug.put("userId", user.getId());
            debug.put("email", user.getEmail());
            debug.put("notificationEnabled", user.isNotificationEnabled());
            debug.put("fcmToken", user.getFcmToken() != null ? "존재" : "없음");

            // DB에서 직접 알림 조회
            List<NotificationEntity> notifications = notificationRepository
                    .findAllByUserOrderByCreatedAtDesc(user);
            debug.put("totalNotifications", notifications.size());

            // 타입별 알림 수
            Map<NotificationType, Long> typeCount = notifications.stream()
                    .collect(Collectors.groupingBy(
                            NotificationEntity::getType,
                            Collectors.counting()
                    ));
            debug.put("notificationsByType", typeCount);

            // 최근 5개 알림 상세
            List<Map<String, Object>> recentNotifications = notifications.stream()
                    .limit(5)
                    .map(n -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("id", n.getId());
                        info.put("type", n.getType());
                        info.put("message", n.getMessage());
                        info.put("createdAt", n.getCreatedAt());
                        info.put("isRead", n.isRead());
                        return info;
                    })
                    .collect(Collectors.toList());
            debug.put("recentNotifications", recentNotifications);

            return ResponseEntity.ok(ApiResponse.onSuccess(debug));
        } catch (Exception e) {
            log.error("알림 디버깅 실패: 사용자={}, 에러={}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.onFailure("DEBUG_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/send-all-types/{userId}")
    @Operation(summary = "모든 타입 알림 테스트 전송")
    public ResponseEntity<ApiResponse<String>> sendAllTypeNotifications(@PathVariable Long userId) {
        try {
            User user = userService.getUserById(userId);

            // 각 타입별로 알림 전송
            notificationService.sendChallengeNotification(user, "챌린지 테스트 알림", 1L, user.getFcmToken());
            notificationService.sendReplyNotification(user, "댓글 테스트 알림", 1L, user.getFcmToken());
            notificationService.sendStepNotification(user, "걸음수 테스트 알림", user.getFcmToken());
            notificationService.notifyCommentReply(user, 1L, "대댓글 테스트 알림");
            notificationService.notifyLikeMilestone(user, 50, 1L);

            return ResponseEntity.ok(ApiResponse.onSuccess("모든 타입 알림 전송 완료"));
        } catch (Exception e) {
            log.error("전체 알림 테스트 실패: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.onFailure("ALL_TYPE_TEST_FAILED", e.getMessage()));
        }
    }
}