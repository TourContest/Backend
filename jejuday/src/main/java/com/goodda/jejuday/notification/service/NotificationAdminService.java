package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.dto.NotificationStatsDto;
import com.goodda.jejuday.notification.dto.NotificationTypeCountDto;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationAdminService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 시스템 전체 알림 통계 조회
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSystemNotificationStats() {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime monthAgo = now.minusDays(30);

        // 기본 통계
        long totalNotifications = notificationRepository.count();
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByIsNotificationEnabledTrue(); // 수정됨

        // 기간별 통계
        stats.put("totalNotifications", totalNotifications);
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("userActivationRate", calculatePercentage(activeUsers, totalUsers));

        // 타입별 통계
        Map<NotificationType, Long> typeStats = Arrays.stream(NotificationType.values())
                .collect(Collectors.toMap(
                        type -> type,
                        type -> notificationRepository.countByType(type)
                ));
        stats.put("notificationsByType", typeStats);

        // 최근 활동 통계
        stats.put("recentActivity", getRecentActivityStats(weekAgo, monthAgo));

        return stats;
    }

    /**
     * 특정 사용자의 알림 통계 조회
     */
    @Transactional(readOnly = true)
    public NotificationStatsDto getUserNotificationStats(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        long totalCount = notificationRepository.countByUser(user);
        long unreadCount = notificationRepository.countByUserAndIsRead(user, false);
        long recentCount = notificationRepository.countRecentNotifications(
                user, LocalDateTime.now().minusDays(7)
        );

        NotificationStatsDto stats = NotificationStatsDto.of(totalCount, unreadCount);
        return NotificationStatsDto.builder()
                .totalCount(stats.getTotalCount())
                .unreadCount(stats.getUnreadCount())
                .readCount(stats.getReadCount())
                .recentCount(recentCount)
                .readPercentage(stats.getReadPercentage())
                .build();
    }

    /**
     * 알림 타입별 통계 조회
     */
    @Transactional(readOnly = true)
    public List<NotificationTypeCountDto> getNotificationTypeStats(LocalDateTime startDate, LocalDateTime endDate) {
        List<NotificationTypeCountDto> typeStats = new ArrayList<>();

        for (NotificationType type : NotificationType.values()) {
            long totalCount = (startDate != null && endDate != null)
                    ? notificationRepository.countByTypeAndCreatedAtBetween(type, startDate, endDate)
                    : notificationRepository.countByType(type);

            long unreadCount = notificationRepository.countByTypeAndIsRead(type, false);

            typeStats.add(NotificationTypeCountDto.of(type, totalCount, unreadCount));
        }

        return typeStats.stream()
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
    }

    /**
     * 오래된 알림 정리
     */
    @Transactional
    public int cleanupOldNotifications(int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        int deletedCount = notificationRepository.deleteOldNotifications(cutoffDate);

        log.info("오래된 알림 정리 완료: 기준일={}, 삭제된 알림 수={}", cutoffDate, deletedCount);
        return deletedCount;
    }

    /**
     * 읽은 알림 정리
     */
    @Transactional
    public int cleanupReadNotifications() {
        List<User> users = userRepository.findAll();
        int totalDeleted = 0;

        for (User user : users) {
            int deleted = notificationRepository.deleteReadNotificationsByUser(user);
            totalDeleted += deleted;
        }

        log.info("읽은 알림 정리 완료: 총 삭제된 알림 수={}", totalDeleted);
        return totalDeleted;
    }

    /**
     * 시스템 상태 확인
     */
    public Map<String, Object> checkSystemHealth() {
        Map<String, Object> health = new HashMap<>();

        try {
            // DB 연결 상태 확인
            long userCount = userRepository.count();
            health.put("databaseConnection", "OK");
            health.put("activeUsers", userCount);

            // Redis 연결 상태 확인
            redisTemplate.opsForValue().set("health:check", "ok");
            String redisCheck = redisTemplate.opsForValue().get("health:check");
            health.put("redisConnection", "ok".equals(redisCheck) ? "OK" : "FAILED");

            // 최근 알림 발송 상태
            LocalDateTime recentTime = LocalDateTime.now().minusHours(1);
            long recentNotifications = notificationRepository.countByCreatedAtAfter(recentTime);
            health.put("recentNotifications", recentNotifications);

            // FCM 토큰 상태
            long usersWithTokens = userRepository.countByFcmTokenIsNotNull();
            health.put("usersWithFcmTokens", usersWithTokens);
            health.put("fcmTokenCoverage", calculatePercentage(usersWithTokens, userCount));

            health.put("status", "HEALTHY");
            health.put("checkTime", LocalDateTime.now());

        } catch (Exception e) {
            health.put("status", "UNHEALTHY");
            health.put("error", e.getMessage());
            log.error("시스템 상태 확인 중 오류 발생", e);
        }

        return health;
    }

    /**
     * 전체 사용자에게 공지 알림 발송
     */
    @Transactional
    public int broadcastNotification(String message) {
        List<User> activeUsers = userRepository.findByIsNotificationEnabledTrueAndFcmTokenIsNotNull(); // 수정됨
        int sentCount = 0;

        for (User user : activeUsers) {
            try {
                notificationService.sendNotificationInternal(
                        user,
                        message,
                        NotificationType.POPULARITY, // 공지사항은 POPULARITY 타입으로 분류
                        "broadcast:" + System.currentTimeMillis(),
                        user.getFcmToken()
                );
                sentCount++;
            } catch (Exception e) {
                log.warn("사용자 {}에게 브로드캐스트 알림 발송 실패: {}", user.getId(), e.getMessage());
            }
        }

        log.info("브로드캐스트 알림 발송 완료: 대상={}, 성공={}", activeUsers.size(), sentCount);
        return sentCount;
    }

    /**
     * 실패한 FCM 토큰 조회 (Redis 기반)
     */
    public List<String> getFailedFcmTokens() {
        Set<String> failedTokenKeys = redisTemplate.keys("fcm:failed:*");

        return failedTokenKeys.stream()
                .map(key -> key.replace("fcm:failed:", ""))
                .collect(Collectors.toList());
    }

    /**
     * 모든 알림 관련 캐시 삭제
     */
    public void clearAllNotificationCache() {
        // 알림 관련 캐시 패턴들
        String[] patterns = {
                "NOTIFY:*",
                "spot:score:*",
                "spot:likes:*",
                "spot:replies:*",
                "attendance:checked:*",
                "promotion:executed:*"
        };

        for (String pattern : patterns) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("캐시 패턴 {} 삭제 완료: {}개 키", pattern, keys.size());
            }
        }
    }

    // 헬퍼 메서드들
    private double calculatePercentage(long numerator, long denominator) {
        if (denominator == 0) return 0.0;
        return Math.round((double) numerator / denominator * 100 * 10) / 10.0;
    }

    private Map<String, Object> getRecentActivityStats(LocalDateTime weekAgo, LocalDateTime monthAgo) {
        Map<String, Object> activity = new HashMap<>();

        long weeklyNotifications = notificationRepository.countByCreatedAtAfter(weekAgo);
        long monthlyNotifications = notificationRepository.countByCreatedAtAfter(monthAgo);

        activity.put("weeklyNotifications", weeklyNotifications);
        activity.put("monthlyNotifications", monthlyNotifications);
        activity.put("dailyAverage", weeklyNotifications / 7.0);

        return activity;
    }
}