package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * notification_outbox 수명 관리 스케줄러.
 * 처리 완료된 SENT/FAILED row를 주기적으로 삭제해 테이블 무한 증가를 방지한다.
 * ShedLock으로 다중 인스턴스 중복 실행을 방지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupScheduler {

    // 보존 기간: SENT/FAILED row를 이 기간 이후 삭제
    private static final int RETENTION_DAYS = 30;

    private final NotificationOutboxRepository outboxRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "outboxCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = outboxRepository.deleteOldTerminalRows(cutoff);
        log.info("outbox 정리 완료: {}건 삭제 (보존기간={}일, 기준={})", deleted, RETENTION_DAYS, cutoff);
    }
}
