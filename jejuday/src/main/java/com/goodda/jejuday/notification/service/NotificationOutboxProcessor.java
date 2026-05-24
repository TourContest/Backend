package com.goodda.jejuday.notification.service;

import static com.goodda.jejuday.notification.util.NotificationConstants.FCM_DEFAULT_TITLE;

import com.goodda.jejuday.notification.entity.NotificationOutbox;
import com.goodda.jejuday.notification.port.PushNotificationSender;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxProcessor {

    private static final int BATCH_SIZE = 50;

    private final NotificationOutboxRepository outboxRepository;
    private final PushNotificationSender pushSender;

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void processPendingOutbox() {
        List<NotificationOutbox> pending = outboxRepository.findPendingEntries(
                LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));

        if (pending.isEmpty()) return;

        log.info("Outbox 재처리 시작: {}건", pending.size());
        int success = 0;
        int failed = 0;

        for (NotificationOutbox outbox : pending) {
            try {
                outbox.markProcessing();
                pushSender.send(outbox.getTargetToken(), FCM_DEFAULT_TITLE, outbox.getMessage());
                outbox.markDone();
                success++;
            } catch (Exception e) {
                outbox.markFailed(e.getMessage());
                failed++;
                log.warn("Outbox 재처리 실패: id={}, retry={}, error={}",
                        outbox.getId(), outbox.getRetryCount(), e.getMessage());
            }
        }

        outboxRepository.saveAll(pending);
        log.info("Outbox 재처리 완료: success={}, failed={}", success, failed);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupProcessedOutbox() {
        int deleted = outboxRepository.deleteCompleted(LocalDateTime.now().minusDays(7));
        log.info("Outbox 정리 완료: {}건 삭제", deleted);
    }
}
