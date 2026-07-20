package com.goodda.jejuday.pay.service;

import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.pay.repository.PointLedgerRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원장(point_ledger)이 정본이므로, 매일 한 번 SUM(ledger)과 users.hallabong을 대조해
 * 불일치를 원장 값으로 교정한다. 이벤트 경로(PointLedgerService.record)가 정확하면 드리프트는
 * 0건이어야 하며, 드리프트 발생 건수 로그가 곧 "잔액 경로가 정확하다"는 운영 증거가 된다
 * (SpotEngagementReconciliationJob과 동일한 철학).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointLedgerReconciliationJob {

    private final PointLedgerRepository ledgerRepository;
    private final UserRepository userRepository;

    @Scheduled(cron = "0 30 4 * * *") // 매일 새벽 4시 30분 실행 (스팟 보정 배치와 겹치지 않게)
    @SchedulerLock(name = "pointLedgerReconciliation", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void reconcile() {
        Map<Long, Integer> ledgerSums = toSumMap(ledgerRepository.sumAmountGroupByAllUsers());
        List<Object[]> balances = userRepository.findAllIdAndHallabong();

        int driftCount = 0;
        for (Object[] row : balances) {
            Long userId = (Long) row[0];
            int actualBalance = (Integer) row[1];
            int expectedBalance = ledgerSums.getOrDefault(userId, 0);

            if (actualBalance != expectedBalance) {
                driftCount++;
                userRepository.setHallabongExact(userId, expectedBalance);
                log.warn("포인트 잔액 드리프트 수정: userId={}, 기존잔액={}, 원장합계={}",
                        userId, actualBalance, expectedBalance);
            }
        }

        log.info("포인트 원장 보정 배치 완료: 대상={}건, 드리프트 수정={}건", balances.size(), driftCount);
    }

    private Map<Long, Integer> toSumMap(List<Object[]> rows) {
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : rows) {
            Long userId = (Long) row[0];
            long sum = ((Number) row[1]).longValue();
            result.put(userId, (int) sum);
        }
        return result;
    }
}
