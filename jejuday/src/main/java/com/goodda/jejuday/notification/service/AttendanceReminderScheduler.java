package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.attendance.repository.ReminderTarget;
import com.goodda.jejuday.attendance.repository.UserAttendanceRepository;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 출석 리마인더 스케줄러.
 *
 * <p>설계 원칙:
 * - 당일 출석자는 날짜별 단일 Redis Set(attendance:date:{date})으로 관리.
 * - TTL은 다음날 자정 + 1시간으로 설정해 자동 만료.
 * - Redis 조회 실패·미스 시 DB(UserAttendance)로 폴백.
 * - 알림 대상은 ReminderTarget 프로젝션(notificationEnabled=true, fcmToken not null) 사용.
 * - FCM 전송은 outbox 배치 INSERT만 하고 실제 전송은 OutboxPoller에 위임.
 * - ShedLock으로 다중 인스턴스 중복 발송 방지.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceReminderScheduler {

    public static final String ATTENDANCE_SET_KEY = "attendance:date:%s";

    private final UserAttendanceRepository attendanceRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "0 0 12 * * *")
    @SchedulerLock(name = "attendanceReminder", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void sendAttendanceReminders() {
        log.info("출석 리마인더 전송 시작");
        LocalDate today = LocalDate.now();

        List<ReminderTarget> targets = attendanceRepository.findAttendanceReminderTargets();
        Set<Long> attendedIds = getAttendedUserIds(today);

        int inserted = notificationService.scheduleAttendanceReminders(targets, attendedIds, today);
        log.info("출석 리마인더 outbox 삽입 완료: 삽입={}, 전체 대상={}", inserted, targets.size());
    }

    /**
     * 출석 체크 완료 시 당일 Set에 userId를 추가한다.
     * Redis 장애 시 예외를 던지지 않는다 — 호출 측에서 try-catch로 감쌈.
     */
    public void markAttendanceChecked(Long userId, LocalDate date) {
        String key = String.format(ATTENDANCE_SET_KEY, date);
        redisTemplate.opsForSet().add(key, userId.toString());
        // TTL: 다음날 자정 + 1시간 (멱등 — 같은 날 여러 번 호출해도 안전)
        redisTemplate.expire(key, ttlUntilTomorrow(date));
        log.debug("출석 Set 업데이트: userId={}, key={}", userId, key);
    }

    /**
     * 당일 출석자 ID Set을 Redis에서 조회한다.
     * Redis 장애나 Set 미존재 시 DB로 폴백.
     *
     * TODO: 유저 수가 수만 명 이상으로 증가하면 members()가 Redis 이벤트 루프를 블로킹한다.
     *       SSCAN 커서 방식(redisTemplate.opsForSet().scan())으로 전환을 검토할 것.
     */
    private Set<Long> getAttendedUserIds(LocalDate date) {
        String key = String.format(ATTENDANCE_SET_KEY, date);
        try {
            Set<String> members = redisTemplate.opsForSet().members(key);
            if (members != null && !members.isEmpty()) {
                return members.stream().map(Long::valueOf).collect(Collectors.toSet());
            }
        } catch (Exception e) {
            log.warn("Redis 출석 Set 조회 실패, DB fallback 수행: date={}, error={}", date, e.getMessage());
        }
        // cold cache 또는 Redis 장애 → DB에서 당일 출석자 ID 조회
        log.debug("DB fallback: 당일 출석자 조회, date={}", date);
        return attendanceRepository.findAttendedUserIdsByDate(date);
    }

    private Duration ttlUntilTomorrow(LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrowMidnight = date.plusDays(1).atStartOfDay();
        Duration remaining = Duration.between(now, tomorrowMidnight);
        return remaining.isNegative() ? Duration.ofHours(1) : remaining.plusHours(1);
    }
}
