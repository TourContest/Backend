package com.goodda.jejuday.steps.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.steps.dto.PointStatusResponse;
import com.goodda.jejuday.steps.dto.StepRequestDto;
import com.goodda.jejuday.steps.entity.MoodGrade;
import com.goodda.jejuday.steps.entity.StepDaily;
import com.goodda.jejuday.steps.repository.StepDailyRepository;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StepService {

    private final StepDailyRepository stepDailyRepository;
    private final UserRepository userRepository;

    private static final int MAX_DAILY_STEPS = 20_000;
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

        todayRecord.addSteps(dto.stepCount());
        user.setTotalSteps(user.getTotalSteps() + dto.stepCount());
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
//        user.setTotalSteps(user.getTotalSteps() + todayRecord.getTotalSteps());
        todayRecord.addConvertedPoints(actualConvertible);

        checkAndRewardMoodUpgrade(user);

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
}

