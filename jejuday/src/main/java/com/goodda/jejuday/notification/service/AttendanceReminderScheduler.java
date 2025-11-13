package com.goodda.jejuday.notification.service;

import static com.goodda.jejuday.notification.util.NotificationConstants.ATTENDANCE_CACHE_KEY;
import static com.goodda.jejuday.notification.util.NotificationConstants.ATTENDANCE_CACHE_TTL;

import com.goodda.jejuday.attendance.repository.UserAttendanceRepository;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceReminderScheduler {

    private final UserRepository userRepository;
    private final UserAttendanceRepository attendanceRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "0 0 12 * * *") // 매일 12시 정각
    public void sendAttendanceReminders() {
        log.info("출석 리마인더 전송 시작");

        LocalDate today = LocalDate.now();
        List<User> eligibleUsers = getEligibleUsers();
        Set<Long> checkedUserIds = getCachedCheckedUsers(today);

        int sentCount = 0;
        for (User user : eligibleUsers) {
            if (shouldSendReminder(user, today, checkedUserIds)) {
                sendAttendanceReminder(user, today);
                sentCount++;
            }
        }

        log.info("출석 리마인더 전송 완료: 전송={}, 대상={}", sentCount, eligibleUsers.size());
    }

    private List<User> getEligibleUsers() {
        return userRepository.findAll().stream()
                .filter(User::isNotificationEnabled)
                .filter(user -> user.getFcmToken() != null && !user.getFcmToken().isBlank())
                .collect(Collectors.toList());
    }

    private Set<Long> getCachedCheckedUsers(LocalDate date) {
        String pattern = String.format("attendance:checked:%s:*", date);
        Set<String> keys = redisTemplate.keys(pattern);

        return keys.stream()
                .map(key -> {
                    String[] parts = key.split(":");
                    return Long.valueOf(parts[parts.length - 1]);
                })
                .collect(Collectors.toSet());
    }

    private boolean shouldSendReminder(User user, LocalDate today, Set<Long> checkedUserIds) {
        // 캐시에서 먼저 확인
        if (checkedUserIds.contains(user.getId())) {
            return false;
        }

        // 캐시에 없으면 DB에서 확인 후 캐시 업데이트
        boolean isChecked = attendanceRepository.findByUserIdAndCheckDate(user.getId(), today).isPresent();

        if (isChecked) {
            cacheAttendanceCheck(user.getId(), today);
            return false;
        }

        return true;
    }

    private void sendAttendanceReminder(User user, LocalDate today) {
        try {
            String message = "아직 오늘 출석하지 않으셨어요! 한라봉 받으러 오세요";
            String contextKey = "attendance:" + today;

            notificationService.sendNotificationInternal(
                    user,
                    message,
                    NotificationType.ATTENDANCE,
                    contextKey,
                    user.getFcmToken()
            );

            log.debug("출석 리마인더 전송: 사용자={}", user.getId());
        } catch (Exception e) {
            log.error("출석 리마인더 전송 실패: 사용자={}, 에러={}", user.getId(), e.getMessage());
        }
    }

    private void cacheAttendanceCheck(Long userId, LocalDate date) {
        String cacheKey = String.format(ATTENDANCE_CACHE_KEY, date, userId);
        redisTemplate.opsForValue().set(cacheKey, "checked", ATTENDANCE_CACHE_TTL);
    }

    public void markAttendanceChecked(Long userId, LocalDate date) {
        cacheAttendanceCheck(userId, date);
        log.debug("출석 체크 캐시 업데이트: 사용자={}, 날짜={}", userId, date);
    }

    @Scheduled(cron = "0 0 1 * * *") // 매일 새벽 1시
    public void cleanupOldAttendanceCache() {
        log.info("오래된 출석 캐시 정리 시작");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        String pattern = String.format("attendance:checked:%s:*", yesterday);
        Set<String> oldKeys = redisTemplate.keys(pattern);

        if (oldKeys != null && !oldKeys.isEmpty()) {
            redisTemplate.delete(oldKeys);
            log.info("오래된 출석 캐시 정리 완료: 삭제된 키 수={}", oldKeys.size());
        }
    }
}