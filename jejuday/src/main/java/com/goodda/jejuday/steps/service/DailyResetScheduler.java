package com.goodda.jejuday.steps.service;

import com.goodda.jejuday.steps.entity.StepDaily;
import com.goodda.jejuday.steps.repository.StepDailyRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DailyResetScheduler {

    private final StepDailyRepository stepDailyRepository;

    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    @Transactional
    public void resetDailySteps() {
        LocalDate today = LocalDate.now();
        List<StepDaily> all = stepDailyRepository.findAllByDate(today);

        for (StepDaily step : all) {
            step.setTotalSteps(0);
            step.setConvertedPoints(0);
        }
    }
}
