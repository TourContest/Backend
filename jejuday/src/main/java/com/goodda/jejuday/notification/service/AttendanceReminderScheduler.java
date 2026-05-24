package com.goodda.jejuday.notification.service;

import static com.goodda.jejuday.notification.util.NotificationConstants.ATTENDANCE_CACHE_KEY;
import static com.goodda.jejuday.notification.util.NotificationConstants.ATTENDANCE_CACHE_TTL;

import com.goodda.jejuday.attendance.repository.UserAttendanceRepository;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.entity.NotificationType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceReminderScheduler {

    private final UserRepository userRepository;
    private final UserAttendanceRepository attendanceRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationCacheManager cacheManager;

    @Scheduled(cron = "0 0 12 * * *")
    public void sendAttendanceReminders() {
        log.info("출석 리마인더 전송 시작");
        LocalDate today = LocalDate.now();

        // findAll() 대신 알림 허용 + FCM 토큰 보유 유저만 조회
        List<User> eligibleUsers = userRepository.findByIsNotificationEnabledTrueAndFcmTokenIsNotNull();
        Set<Long> cachedCheckedIds = getCachedCheckedUserIds(today);

        int sentCount = 0;
        for (User user : eligibleUsers) {
            if (shouldSendReminder(user, today, cachedCheckedIds)) {
                sendReminder(user, today);
                sentCount++;
            }
        }
        log.info("출석 리마인더 전송 완료: sent={}, eligible={}", sentCount, eligibleUsers.size());
    }

    // KEYS 대신 SCAN 사용
    private Set<Long> getCachedCheckedUserIds(LocalDate date) {
        String pattern = String.format("attendance:checked:%s:*", date);
        Set<String> keys = cacheManager.scanKeys(pattern);
        return keys.stream()
                .map(key -> Long.valueOf(key.substring(key.lastIndexOf(':') + 1)))
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean shouldSendReminder(User user, LocalDate today, Set<Long> cachedIds) {
        if (cachedIds.contains(user.getId())) return false;

        boolean checked = attendanceRepository.findByUserIdAndCheckDate(user.getId(), today).isPresent();
        if (checked) {
            cacheAttendanceCheck(user.getId(), today);
            return false;
        }
        return true;
    }

    private void sendReminder(User user, LocalDate today) {
        try {
            notificationService.sendNotificationInternal(
                    user,
                    "아직 오늘 출석하지 않으셨어요! 한라봉 받으러 오세요",
                    NotificationType.ATTENDANCE,
                    "attendance:" + today,
                    user.getFcmToken()
            );
        } catch (Exception e) {
            log.error("출석 리마인더 전송 실패: userId={}, error={}", user.getId(), e.getMessage());
        }
    }

    public void markAttendanceChecked(Long userId, LocalDate date) {
        cacheAttendanceCheck(userId, date);
        log.debug("출석 체크 캐시 업데이트: userId={}, date={}", userId, date);
    }

    private void cacheAttendanceCheck(Long userId, LocalDate date) {
        String cacheKey = String.format(ATTENDANCE_CACHE_KEY, date, userId);
        redisTemplate.opsForValue().set(cacheKey, "checked", ATTENDANCE_CACHE_TTL);
    }

    @Scheduled(cron = "0 0 1 * * *")
    public void cleanupOldAttendanceCache() {
        log.info("오래된 출석 캐시 정리 시작");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String pattern = String.format("attendance:checked:%s:*", yesterday);
        Set<String> oldKeys = cacheManager.scanKeys(pattern);
        if (!oldKeys.isEmpty()) {
            redisTemplate.delete(oldKeys);
            log.info("출석 캐시 정리 완료: {}건 삭제", oldKeys.size());
        }
    }
}
