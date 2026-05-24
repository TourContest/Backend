package com.goodda.jejuday.steps.service;

import com.goodda.jejuday.attendance.repository.UserAttendanceRepository;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.steps.entity.StepDaily;
import com.goodda.jejuday.steps.repository.StepDailyRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyStartBonusServiceImpl implements DailyStartBonusService {

    private final UserRepository userRepository;
    private final StepDailyRepository stepDailyRepository;
    private final UserAttendanceRepository attendanceRepository; // 추가

    // 보너스 기준 상수
    private static final long BONUS_THRESHOLD_HIGH = 15000; // 1.5만보 이상
    private static final long BONUS_THRESHOLD_LOW = 10000;  // 1만보 이상
    private static final long HIGH_BONUS_STEPS = 1500;     // 1.5만보 미만 시 보너스
    private static final long LOW_BONUS_STEPS = 3000;      // 1만보 미만 시 보너스

    @Override
    @Transactional(readOnly = true)
    public long calculateStartBonus(User user) {
        // 첫날 여부 확인 - 출석 기록이나 걸음수 기록이 있는지 체크
        if (isFirstDayUser(user)) {
            log.info("첫날 사용자 {}는 시작 보너스 대상 아님", user.getId());
            return 0L;
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);

        return stepDailyRepository.findPreviousDaySteps(user, yesterday)
                .map(yesterday_record -> {
                    long yesterdaySteps = yesterday_record.getTotalSteps();

                    if (yesterdaySteps < BONUS_THRESHOLD_LOW) {
                        // 전날 1만보 미만 → 3천보 보너스
                        log.info("전날 1만보 미만 사용자 {}에게 3천보 보너스 지급 예정 (전날: {}보)",
                                user.getId(), yesterdaySteps);
                        return LOW_BONUS_STEPS;
                    } else if (yesterdaySteps < BONUS_THRESHOLD_HIGH) {
                        // 전날 1만보 이상 1.5만보 미만 → 1500보 보너스
                        log.info("전날 1만보 이상 1.5만보 미만 사용자 {}에게 1500보 보너스 지급 예정 (전날: {}보)",
                                user.getId(), yesterdaySteps);
                        return HIGH_BONUS_STEPS;
                    } else {
                        // 전날 1.5만보 이상 → 보너스 없음
                        log.info("전날 1.5만보 이상 사용자 {}는 시작 보너스 대상 아님 (전날: {}보)",
                                user.getId(), yesterdaySteps);
                        return 0L;
                    }
                })
                .orElseGet(() -> {
                    // 전날 기록이 없지만 첫날이 아닌 경우 → 3천보 보너스
                    log.info("전날 기록이 없는 기존 사용자 {}에게 3천보 보너스 지급 예정", user.getId());
                    return LOW_BONUS_STEPS;
                });
    }

    /**
     * 첫날 사용자인지 확인
     * 출석 기록이나 걸음수 기록이 하나도 없으면 첫날로 판단
     */
    private boolean isFirstDayUser(User user) {
        LocalDate today = LocalDate.now();

        // 1. 출석 기록 확인 (오늘 이전에 출석한 적이 있는지)
        boolean hasAttendanceHistory = attendanceRepository.findByUserIdAndCheckDate(user.getId(), today.minusDays(1))
                .isPresent() ||
                attendanceRepository.existsByUserIdAndCheckDateBefore(user.getId(), today);

        // 2. 걸음수 기록 확인 (오늘 이전에 걸음수 기록이 있는지)
        List<StepDaily> recentRecords = stepDailyRepository.findRecentDays(user, today.minusDays(30));
        boolean hasStepHistory = recentRecords.stream()
                .anyMatch(record -> record.getDate().isBefore(today));

        // 출석 기록이나 걸음수 기록이 하나라도 있으면 첫날이 아님
        boolean isFirstDay = !hasAttendanceHistory && !hasStepHistory;

        if (isFirstDay) {
            log.info("첫날 사용자 확인: 사용자={}, 출석기록={}, 걸음수기록={}",
                    user.getId(), hasAttendanceHistory, hasStepHistory);
        }

        return isFirstDay;
    }

    @Override
    @Transactional
    public long applyStartBonus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        LocalDate today = LocalDate.now();

        // 오늘의 걸음수 기록 조회 또는 생성
        StepDaily todayRecord = stepDailyRepository.findByUserAndDate(user, today)
                .orElseGet(() -> stepDailyRepository.save(StepDaily.builder()
                        .user(user)
                        .date(today)
                        .totalSteps(0)
                        .convertedPoints(0)
                        .build()));

        // 이미 시작 보너스가 적용된 경우
        if (todayRecord.isStartBonusApplied()) {
            log.warn("사용자 {}는 오늘 이미 시작 보너스를 적용받았습니다.", userId);
            return 0L;
        }

        // 보너스 계산 및 적용
        long bonusSteps = calculateStartBonus(user);

        if (bonusSteps > 0) {
            todayRecord.applyStartBonus(bonusSteps);

            // 사용자 총 걸음수에도 반영
            user.setTotalSteps(user.getTotalSteps() + bonusSteps);

            stepDailyRepository.save(todayRecord);
            userRepository.save(user);

            log.info("시작 보너스 적용 완료: 사용자={}, 보너스={}보, 오늘총걸음={}보",
                    userId, bonusSteps, todayRecord.getTotalSteps());
        }

        return bonusSteps;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canApplyStartBonus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        LocalDate today = LocalDate.now();

        return stepDailyRepository.findByUserAndDate(user, today)
                .map(record -> !record.isStartBonusApplied())
                .orElse(true); // 오늘 기록이 없으면 적용 가능
    }
}
