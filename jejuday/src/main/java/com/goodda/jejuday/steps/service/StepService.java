package com.goodda.jejuday.steps.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.service.NotificationService;
import com.goodda.jejuday.steps.dto.PointStatusResponse;
import com.goodda.jejuday.steps.dto.StepRequestDto;
import com.goodda.jejuday.steps.entity.MoodGrade;
import com.goodda.jejuday.steps.entity.StepDaily;
import com.goodda.jejuday.steps.repository.StepDailyRepository;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StepService {

    private final StepDailyRepository stepDailyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private static final int MAX_DAILY_STEPS = 20_000;
    private static final int DAILY_GOAL_STEPS = 20_000; // 일일 목표 걸음수
    private static final int MAX_DAILY_POINTS = 2000;
    private static final int POINT_CONVERSION_RATE = 10; // 10걸음당 1포인트

    @Transactional
    public void recordSteps(Long userId, StepRequestDto dto) {
        User user = getUser(userId);
        LocalDate today = LocalDate.now();

        StepDaily todayRecord = stepDailyRepository.findByUserAndDate(user, today)
                .orElseGet(() -> stepDailyRepository.save(StepDaily.builder()
                        .user(user)
                        .date(today)
                        .totalSteps(0)
                        .convertedPoints(0)
                        .build()));

        // 기존 걸음수 저장
        long previousSteps = todayRecord.getTotalSteps();
        todayRecord.addSteps(dto.stepCount());
        long currentSteps = todayRecord.getTotalSteps();

        // 사용자 총 걸음수 업데이트
        user.setTotalSteps(user.getTotalSteps() + dto.stepCount());

        // 2만보 달성 체크 및 알림 전송
        checkAndSendGoalAchievementNotification(user, previousSteps, currentSteps, today);

        log.info("걸음수 기록 완료: 사용자={}, 추가걸음={}, 오늘총걸음={}",
                userId, dto.stepCount(), currentSteps);
    }

    private void checkAndSendGoalAchievementNotification(User user, long previousSteps,
                                                         long currentSteps, LocalDate date) {
        // 이전에는 2만보 미달성, 현재는 2만보 달성한 경우에만 알림 전송
        if (previousSteps < DAILY_GOAL_STEPS && currentSteps >= DAILY_GOAL_STEPS) {
            try {
                String message = String.format("오늘 목표 2만보 달성! 현재 %s보를 걸었어요! 대단해요!",
                        String.format("%,d", currentSteps));

                notificationService.sendStepNotification(user, message, user.getFcmToken());

                log.info("2만보 달성 알림 전송: 사용자={}, 걸음수={}", user.getId(), currentSteps);
            } catch (Exception e) {
                log.error("2만보 달성 알림 전송 실패: 사용자={}, 에러={}", user.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public int convertStepsToPoints(Long userId, int requestedPoints) {
        if (requestedPoints <= 0 || requestedPoints % 10 != 0) {
            throw new IllegalArgumentException("포인트는 10 단위로만 전환 가능합니다.");
        }

        User user = getUser(userId);
        LocalDate today = LocalDate.now();

        StepDaily todayRecord = stepDailyRepository.findByUserAndDate(user, today)
                .orElseThrow(() -> new IllegalStateException("걸음수 기록 없음"));

        long convertibleSteps = Math.min(todayRecord.getTotalSteps(), MAX_DAILY_STEPS);
        int alreadyConverted = todayRecord.getConvertedPoints();

        int availablePoints = (int)(convertibleSteps / POINT_CONVERSION_RATE) - alreadyConverted;
        availablePoints = (availablePoints / 10) * 10;

        int todayLimit = MAX_DAILY_POINTS - alreadyConverted;

        int actualConvertible = Math.min(requestedPoints, Math.min(availablePoints, todayLimit));
        if (actualConvertible <= 0) {
            return 0;
        }

        // 포인트 지급
        user.setHallabong(user.getHallabong() + actualConvertible);
        todayRecord.addConvertedPoints(actualConvertible);

        checkAndRewardMoodUpgrade(user);

        log.info("포인트 전환 완료: 사용자={}, 전환포인트={}, 총보유포인트={}",
                userId, actualConvertible, user.getHallabong());

        return actualConvertible;
    }

    public int getRemainingConvertiblePoints(User user) {
        StepDaily todayRecord = stepDailyRepository.findByUserAndDate(user, LocalDate.now())
                .orElse(null);

        if (todayRecord == null) return MAX_DAILY_POINTS;

        long steps = Math.min(todayRecord.getTotalSteps(), MAX_DAILY_STEPS);
        int alreadyConverted = todayRecord.getConvertedPoints();

        int availablePoints = (int)(steps / POINT_CONVERSION_RATE) - alreadyConverted;
        availablePoints = (availablePoints / 10) * 10;

        return Math.max(0, Math.min(MAX_DAILY_POINTS - alreadyConverted, availablePoints));
    }

    private void checkAndRewardMoodUpgrade(User user) {
        MoodGrade current = user.getMoodGrade();
        if (!user.getReceivedMoodGrades().contains(current)) {
            int reward = current.getReward();
            if (reward > 0) {
                user.setHallabong(user.getHallabong() + reward);
                user.getReceivedMoodGrades().add(current);

                log.info("무드 등급 보상 지급: 사용자={}, 등급={}, 보상={}",
                        user.getId(), current, reward);
            }
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));
    }

    public Set<MoodGrade> getReceivedRewardGrades(Long userId) {
        return getUser(userId).getReceivedMoodGrades();
    }

    @Transactional(readOnly = true)
    public PointStatusResponse getPointStatus(Long userId) {
        User user = getUser(userId);
        return new PointStatusResponse(user.getHallabong(), user.getMoodGrade());
    }

    /**
     * 오늘 걸음수 조회 (디버깅용)
     */
    @Transactional(readOnly = true)
    public long getTodaySteps(Long userId) {
        User user = getUser(userId);
        LocalDate today = LocalDate.now();

        return stepDailyRepository.findByUserAndDate(user, today)
                .map(StepDaily::getTotalSteps)
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public boolean isGoalAchievedToday(Long userId) {
        return getTodaySteps(userId) >= DAILY_GOAL_STEPS;
    }
}