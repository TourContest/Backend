package com.goodda.jejuday.attendance.service;

import com.goodda.jejuday.attendance.dto.AttendanceResult;
import com.goodda.jejuday.attendance.entity.UserAttendance;
import com.goodda.jejuday.attendance.entity.UserBonusLog;
import com.goodda.jejuday.attendance.repository.UserAttendanceRepository;
import com.goodda.jejuday.attendance.repository.UserBonusLogRepository;
import com.goodda.jejuday.attendance.util.HallabongConstants;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.common.exception.UserNotFoundException;
import com.goodda.jejuday.notification.service.AttendanceReminderScheduler;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final UserAttendanceRepository attendanceRepository;
    private final UserBonusLogRepository bonusLogRepository;
    private final HallabongService hallabongService;
    private final UserRepository userRepository;
    private final AttendanceReminderScheduler attendanceReminderScheduler;

    @Transactional
    public AttendanceResult checkAttendance(Long userId) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        if (isAlreadyChecked(userId, today)) {
            return AttendanceResult.ofAlreadyChecked();
        }

        User user = getUser(userId);
        int consecutiveDays = calculateConsecutiveDays(userId, today);

        AttendanceReward reward = calculateReward(userId, consecutiveDays, today);

        saveAttendanceRecord(user, today, consecutiveDays);
        hallabongService.addHallabong(userId, reward.total());

        // 출석 체크 완료 후 캐시 업데이트
        try {
            attendanceReminderScheduler.markAttendanceChecked(userId, today);
            log.debug("출석 체크 캐시 업데이트 완료: 사용자={}", userId);
        } catch (Exception e) {
            log.warn("출석 체크 캐시 업데이트 실패: 사용자={}, 에러={}", userId, e.getMessage());
            // 캐시 업데이트 실패는 출석 체크 자체에는 영향을 주지 않음
        }

        int totalHallabong = hallabongService.getHallabong(userId);

        return AttendanceResult.ofSuccess(consecutiveDays, reward.base(), reward.bonus(), totalHallabong);
    }

    private boolean isAlreadyChecked(Long userId, LocalDate date) {
        return attendanceRepository.findByUserIdAndCheckDate(userId, date).isPresent();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId));
    }

    private int calculateConsecutiveDays(Long userId, LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        return attendanceRepository.findByUserIdAndCheckDate(userId, yesterday)
                .map(attendance -> attendance.getConsecutiveDays() + 1)
                .orElse(1);
    }

    private AttendanceReward calculateReward(Long userId, int consecutiveDays, LocalDate today) {
        int baseHallabong = calculateBaseHallabong(consecutiveDays);
        int bonusHallabong = calculateBonusHallabong(userId, consecutiveDays, today);

        return new AttendanceReward(baseHallabong, bonusHallabong);
    }

    private int calculateBaseHallabong(int consecutiveDays) {
        return Math.min(
                HallabongConstants.ATTENDANCE_BASE_HALLABONG
                        + (consecutiveDays - 1) * HallabongConstants.ATTENDANCE_DAILY_INCREMENT,
                HallabongConstants.ATTENDANCE_MAX_HALLABONG
        );
    }

    private int calculateBonusHallabong(Long userId, int consecutiveDays, LocalDate today) {
        if (consecutiveDays % HallabongConstants.ATTENDANCE_BONUS_CYCLE != 0) {
            return 0;
        }

        if (bonusLogRepository.existsByUserIdAndBonusTypeAndGivenDate(
                userId, HallabongConstants.ATTENDANCE_BONUS_TYPE, today)) {
            return 0;
        }

        // 보너스 로그 저장은 별도 메서드로 분리
        saveBonusLog(userId, today);
        return HallabongConstants.ATTENDANCE_BONUS_AMOUNT;
    }

    private void saveBonusLog(Long userId, LocalDate today) {
        User user = getUser(userId);
        UserBonusLog bonusLog = new UserBonusLog(user, HallabongConstants.ATTENDANCE_BONUS_TYPE, today);
        bonusLogRepository.save(bonusLog);
    }

    private void saveAttendanceRecord(User user, LocalDate today, int consecutiveDays) {
        UserAttendance attendance = UserAttendance.builder()
                .user(user)
                .checkDate(today)
                .consecutiveDays(consecutiveDays)
                .hallabongGiven(true)
                .build();

        attendanceRepository.save(attendance);
    }

    // 내부 레코드로 보상 정보 캡슐화
    private record AttendanceReward(int base, int bonus) {
        public int total() {
            return base + bonus;
        }
    }
}