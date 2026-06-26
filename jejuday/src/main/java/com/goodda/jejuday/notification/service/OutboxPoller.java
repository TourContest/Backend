package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.notification.entity.NotificationOutbox;
import com.goodda.jejuday.notification.entity.OutboxStatus;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.service.OutboxTransactionHelper.PollResult;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 폴러.
 *
 * <p>TX 분리 설계:
 * - TX1(OutboxTransactionHelper.claimBatch): PENDING→PROCESSING 선점, 즉시 커밋 → lock 해제
 * - FCM I/O: 트랜잭션 밖에서 수행 (DB lock/커넥션 비점유)
 * - TX2(OutboxTransactionHelper.applyResults): 전송 결과 반영, 즉시 커밋
 *
 * <p>유실 방지: sweepStuckProcessing이 PROCESSING 상태로 2분 이상 멈춘 row를 PENDING으로 복구한다.
 */
@Slf4j
@Component
public class OutboxPoller {

    private static final int STUCK_THRESHOLD_MINUTES = 2;

    private final NotificationOutboxRepository outboxRepository;
    private final FcmGateway fcmGateway;
    private final OutboxTransactionHelper txHelper;

    private final Counter sentCounter;
    private final Counter failedInvalidTokenCounter;
    private final Counter failedMaxRetryCounter;

    public OutboxPoller(NotificationOutboxRepository outboxRepository,
                        FcmGateway fcmGateway,
                        OutboxTransactionHelper txHelper,
                        MeterRegistry meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.fcmGateway = fcmGateway;
        this.txHelper = txHelper;

        this.sentCounter = Counter.builder("fcm.outbox.sent")
                .description("FCM 전송 성공 건수")
                .register(meterRegistry);
        this.failedInvalidTokenCounter = Counter.builder("fcm.outbox.failed")
                .description("FCM 전송 실패 건수")
                .tag("reason", "invalid_token")
                .register(meterRegistry);
        this.failedMaxRetryCounter = Counter.builder("fcm.outbox.failed")
                .description("FCM 전송 실패 건수")
                .tag("reason", "max_retry_exceeded")
                .register(meterRegistry);

        Gauge.builder("fcm.outbox.pending", outboxRepository,
                        r -> r.countByStatus(OutboxStatus.PENDING))
                .description("FCM 전송 대기 건수")
                .register(meterRegistry);
    }

    /**
     * 폴링 주기마다 실행. @Transactional 없음 — TX1/TX2는 txHelper에서 각각 커밋한다.
     * FCM I/O 구간에서 DB lock이 잡히지 않는다.
     */
    @Scheduled(fixedDelay = 10_000)
    public void poll() {
        // TX1: 선점 후 즉시 커밋 (lock은 이 블록 안에서만 유지)
        List<NotificationOutbox> batch = txHelper.claimBatch();
        if (batch.isEmpty()) return;

        log.debug("PROCESSING 배치 시작: {}건", batch.size());

        List<Message> messages = batch.stream().map(this::toFcmMessage).toList();

        BatchResponse response;
        try {
            // 트랜잭션 밖 FCM I/O — DB lock/커넥션 비점유
            response = fcmGateway.sendEach(messages);
        } catch (Exception e) {
            // 서킷 오픈 / 네트워크 장애 → 즉시 PENDING 복구 (크래시의 경우 sweeper가 처리)
            log.warn("FCM 배치 전송 실패, PROCESSING→PENDING 복구: {}", e.getMessage());
            txHelper.resetBatchToPending(batch);
            return;
        }

        // TX2: 전송 결과 반영 후 즉시 커밋
        PollResult result = txHelper.applyResults(batch, response);
        sentCounter.increment(result.sent());
        failedInvalidTokenCounter.increment(result.invalidTokenFailed());
        failedMaxRetryCounter.increment(result.maxRetryFailed());
    }

    /**
     * PROCESSING 상태로 2분 이상 멈춘 row를 PENDING으로 복구.
     * 앱 크래시로 TX2가 실행되지 못한 경우를 처리한다.
     */
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void sweepStuckProcessing() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        int recovered = outboxRepository.resetStuckProcessing(threshold);
        if (recovered > 0) {
            log.warn("PROCESSING 유실 row {}건 PENDING 복구 (threshold={}분)", recovered, STUCK_THRESHOLD_MINUTES);
        }
    }

    private Message toFcmMessage(NotificationOutbox outbox) {
        return Message.builder()
                .setToken(outbox.getFcmToken())
                .setNotification(Notification.builder()
                        .setTitle(outbox.getTitle())
                        .setBody(outbox.getBody())
                        .build())
                .build();
    }
}
