package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.dto.NotificationStatsDto;
import com.goodda.jejuday.notification.dto.NotificationTypeCountDto;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.notification.service.NotificationFactory;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationAdminService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional(readOnly = true)
    public Map<String, Object> getSystemNotificationStats() {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime monthAgo = now.minusDays(30);

        long totalNotifications = notificationRepository.count();
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByIsNotificationEnabledTrue();

        stats.put("totalNotifications", totalNotifications);
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("userActivationRate", calculatePercentage(activeUsers, totalUsers));

        Map<NotificationType, Long> typeStats = Arrays.stream(NotificationType.values())
                .collect(Collectors.toMap(
                        type -> type,
                        type -> notificationRepository.countByType(type)
                ));
        stats.put("notificationsByType", typeStats);
        stats.put("recentActivity", getRecentActivityStats(weekAgo, monthAgo));

        return stats;
    }

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

    @Transactional
    public int cleanupOldNotifications(int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        int deletedCount = notificationRepository.deleteOldNotifications(cutoffDate);
        log.info("오래된 알림 정리 완료: 기준일={}, 삭제된 알림 수={}", cutoffDate, deletedCount);
        return deletedCount;
    }

    @Transactional
    public int cleanupReadNotifications() {
        int deleted = notificationRepository.deleteAllReadNotifications();
        log.info("읽은 알림 정리 완료: 삭제된 알림 수={}", deleted);
        return deleted;
    }

    public Map<String, Object> checkSystemHealth() {
        Map<String, Object> health = new HashMap<>();

        try {
            long userCount = userRepository.count();
            health.put("databaseConnection", "OK");
            health.put("activeUsers", userCount);

            redisTemplate.opsForValue().set("health:check", "ok");
            String redisCheck = redisTemplate.opsForValue().get("health:check");
            health.put("redisConnection", "ok".equals(redisCheck) ? "OK" : "FAILED");

            LocalDateTime recentTime = LocalDateTime.now().minusHours(1);
            long recentNotifications = notificationRepository.countByCreatedAtAfter(recentTime);
            health.put("recentNotifications", recentNotifications);

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

    @Transactional
    public int broadcastNotification(String message) {
        List<User> activeUsers = userRepository.findByIsNotificationEnabledTrueAndFcmTokenIsNotNull();
        String contextKey = "broadcast:" + System.currentTimeMillis();
        int sentCount = 0;

        for (User user : activeUsers) {
            try {
                notificationService.send(NotificationFactory.promotion(
                        user, message, NotificationType.POPULARITY, contextKey));
                sentCount++;
            } catch (Exception e) {
                log.warn("사용자 {}에게 브로드캐스트 알림 발송 실패: {}", user.getId(), e.getMessage());
            }
        }

        log.info("브로드캐스트 알림 발송 완료: 대상={}, 성공={}", activeUsers.size(), sentCount);
        return sentCount;
    }

    /**
     * FCM 전송 실패 토큰 조회 — 실패 토큰은 DB(notification_outbox.status=FAILED)에서 관리.
     * Redis "fcm:failed:*" 키 패턴은 이 시스템에서 사용하지 않으므로 빈 목록 반환.
     */
    public List<String> getFailedFcmTokens() {
        return Collections.emptyList();
    }

    public void clearAllNotificationCache() {
        String[] patterns = {
                "NOTIFY:*",
                "spot:score:*",
                "spot:likes:*",
                "spot:replies:*",
                "attendance:date:*",
                "promotion:executed:*"
        };

        for (String pattern : patterns) {
            scanAndDelete(pattern);
            log.info("캐시 패턴 {} 삭제 완료", pattern);
        }
    }

    private void scanAndDelete(String pattern) {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    connection.del(cursor.next());
                }
            } catch (Exception e) {
                log.warn("캐시 패턴 {} 삭제 중 오류: {}", pattern, e.getMessage());
            }
            return null;
        });
    }

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
