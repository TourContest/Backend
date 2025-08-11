package com.goodda.jejuday.notification.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.notification.dto.NotificationStatsDto;
import com.goodda.jejuday.notification.dto.NotificationTypeCountDto;
import com.goodda.jejuday.notification.service.NotificationAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/admin/notification")
@RequiredArgsConstructor
@Tag(name = "알림 관리자 API", description = "관리자용 알림 시스템 관리 및 모니터링 API")
@PreAuthorize("hasRole('ADMIN')")
public class NotificationAdminController {

    private final NotificationAdminService notificationAdminService;

    @GetMapping("/stats")
    @Operation(summary = "전체 알림 통계 조회", description = "시스템 전체의 알림 통계를 조회합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemStats() {
        try {
            Map<String, Object> stats = notificationAdminService.getSystemNotificationStats();

            log.info("전체 알림 통계 조회 완료");
            return ResponseEntity.ok(ApiResponse.onSuccess(stats));
        } catch (Exception e) {
            log.error("전체 알림 통계 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.onFailure("STATS_FETCH_FAILED", "통계 조회에 실패했습니다."));
        }
    }

    @GetMapping("/stats/user/{userId}")
    @Operation(summary = "특정 사용자 알림 통계", description = "특정 사용자의 알림 통계를 조회합니다.")
    public ResponseEntity<ApiResponse<NotificationStatsDto>> getUserStats(
            @Parameter(description = "사용자 ID", example = "1")
            @PathVariable @NotNull @Positive Long userId) {
        try {
            NotificationStatsDto stats = notificationAdminService.getUserNotificationStats(userId);

            log.info("사용자 알림 통계 조회 완료: 사용자={}", userId);
            return ResponseEntity.ok(ApiResponse.onSuccess(stats));
        } catch (Exception e) {
            log.error("사용자 알림 통계 조회 실패: 사용자={}, 에러={}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.onFailure("USER_STATS_FAILED", "사용자 통계 조회에 실패했습니다."));
        }
    }

    @GetMapping("/stats/type")
    @Operation(summary = "알림 타입별 통계", description = "알림 타입별 발송 통계를 조회합니다.")
    public ResponseEntity<ApiResponse<List<NotificationTypeCountDto>>> getTypeStats(
            @Parameter(description = "시작 날짜", example = "2025-01-01T00:00:00")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "종료 날짜", example = "2025-01-31T23:59:59")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        try {
            List<NotificationTypeCountDto> stats = notificationAdminService
                    .getNotificationTypeStats(startDate, endDate);

            log.info("알림 타입별 통계 조회 완료: 기간={}~{}", startDate, endDate);
            return ResponseEntity.ok(ApiResponse.onSuccess(stats));
        } catch (Exception e) {
            log.error("알림 타입별 통계 조회 실패: 에러={}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.onFailure("TYPE_STATS_FAILED", "타입별 통계 조회에 실패했습니다."));
        }
    }

    @PostMapping("/cleanup/old")
    @Operation(summary = "오래된 알림 정리", description = "지정된 기간보다 오래된 알림들을 삭제합니다.")
    public ResponseEntity<ApiResponse<String>> cleanupOldNotifications(
            @Parameter(description = "삭제할 알림의 기준 날짜 (이전 알림들 삭제)", example = "30")
            @RequestParam(defaultValue = "30") int daysOld) {
        try {
            int deletedCount = notificationAdminService.cleanupOldNotifications(daysOld);

            log.info("오래된 알림 정리 완료: 삭제된 알림 수={}", deletedCount);
            return ResponseEntity.ok(ApiResponse.onSuccess(
                    String.format("%d개의 오래된 알림이 삭제되었습니다.", deletedCount)
            ));
        } catch (Exception e) {
            log.error("오래된 알림 정리 실패: 에러={}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.onFailure("CLEANUP_FAILED", "알림 정리에 실패했습니다."));
        }
    }

    @PostMapping("/cleanup/read")
    @Operation(summary = "읽은 알림 정리", description = "모든 사용자의 읽은 알림들을 삭제합니다.")
    public ResponseEntity<ApiResponse<String>> cleanupReadNotifications() {
        try {
            int deletedCount = notificationAdminService.cleanupReadNotifications();

            log.info("읽은 알림 정리 완료: 삭제된 알림 수={}", deletedCount);
            return ResponseEntity.ok(ApiResponse.onSuccess(
                    String.format("%d개의 읽은 알림이 삭제되었습니다.", deletedCount)
            ));
        } catch (Exception e) {
            log.error("읽은 알림 정리 실패: 에러={}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.onFailure("READ_CLEANUP_FAILED", "읽은 알림 정리에 실패했습니다."));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "알림 시스템 상태 확인", description = "알림 시스템의 현재 상태를 확인합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkNotificationHealth() {
        try {
            Map<String, Object> health = notificationAdminService.checkSystemHealth();

            log.info("알림 시스템 상태 확인 완료");
            return ResponseEntity.ok(ApiResponse.onSuccess(health));
        } catch (Exception e) {
            log.error("알림 시스템 상태 확인 실패: 에러={}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.onFailure("HEALTH_CHECK_FAILED", "시스템 상태 확인에 실패했습니다."));
        }
    }

    @PostMapping("/broadcast")
    @Operation(summary = "전체 사용자 알림 발송", description = "모든 활성 사용자에게 공지 알림을 발송합니다.")
    public ResponseEntity<ApiResponse<String>> broadcastNotification(
            @Parameter(description = "발송할 메시지", example = "시스템 점검이 예정되어 있습니다.")
            @RequestParam @NotNull String message) {
        try {
            int sentCount = notificationAdminService.broadcastNotification(message);

            log.info("전체 알림 발송 완료: 발송 수={}", sentCount);
            return ResponseEntity.ok(ApiResponse.onSuccess(
                    String.format("%d명의 사용자에게 알림이 발송되었습니다.", sentCount)
            ));
        } catch (Exception e) {
            log.error("전체 알림 발송 실패: 에러={}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.onFailure("BROADCAST_FAILED", "전체 알림 발송에 실패했습니다."));
        }
    }

    @GetMapping("/failed-tokens")
    @Operation(summary = "실패한 FCM 토큰 조회", description = "FCM 전송에 실패한 토큰들을 조회합니다.")
    public ResponseEntity<ApiResponse<List<String>>> getFailedTokens() {
        try {
            List<String> failedTokens = notificationAdminService.getFailedFcmTokens();

            log.info("실패한 FCM 토큰 조회 완료: 개수={}", failedTokens.size());
            return ResponseEntity.ok(ApiResponse.onSuccess(failedTokens));
        } catch (Exception e) {
            log.error("실패한 FCM 토큰 조회 실패: 에러={}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.onFailure("FAILED_TOKENS_FETCH_FAILED", "실패한 토큰 조회에 실패했습니다."));
        }
    }

    @PostMapping("/cache/clear")
    @Operation(summary = "알림 캐시 전체 삭제", description = "Redis에 저장된 모든 알림 관련 캐시를 삭제합니다.")
    public ResponseEntity<ApiResponse<String>> clearNotificationCache() {
        try {
            notificationAdminService.clearAllNotificationCache();

            log.info("알림 캐시 전체 삭제 완료");
            return ResponseEntity.ok(ApiResponse.onSuccess("알림 캐시가 모두 삭제되었습니다."));
        } catch (Exception e) {
            log.error("알림 캐시 삭제 실패: 에러={}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.onFailure("CACHE_CLEAR_FAILED", "캐시 삭제에 실패했습니다."));
        }
    }
}