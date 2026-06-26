package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.entity.NotificationOutbox;
import com.goodda.jejuday.notification.entity.OutboxStatus;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OutboxPoller를 위한 TX1/TX2 분리 헬퍼.
 * OutboxPoller가 자기 자신 @Transactional 메서드를 직접 호출하면 Spring 프록시를 우회하므로
 * 별도 빈으로 분리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxTransactionHelper {

    private static final int MAX_RETRY_COUNT = 5;

    private final NotificationOutboxRepository outboxRepository;
    private final UserRepository userRepository;

    /**
     * TX1: PENDING → PROCESSING 선점 후 즉시 커밋.
     * FOR UPDATE SKIP LOCKED 락은 이 트랜잭션 종료 시 해제된다.
     */
    @Transactional
    public List<NotificationOutbox> claimBatch() {
        List<NotificationOutbox> batch = outboxRepository.findPendingBatch();
        if (batch.isEmpty()) return batch;

        LocalDateTime now = LocalDateTime.now();
        for (NotificationOutbox outbox : batch) {
            outbox.setStatus(OutboxStatus.PROCESSING);
            outbox.setProcessingStartedAt(now);
        }
        outboxRepository.saveAll(batch);
        return batch;
    }

    /**
     * TX2: FCM 전송 결과를 SENT / PENDING(재시도) / FAILED 로 일괄 업데이트.
     * 엔티티는 TX1 커밋 후 detached 상태이므로 saveAll이 merge를 수행한다.
     */
    @Transactional
    public PollResult applyResults(List<NotificationOutbox> batch, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        int sent = 0, invalidToken = 0, maxRetry = 0;

        for (int i = 0; i < responses.size(); i++) {
            SendResponse sr = responses.get(i);
            NotificationOutbox outbox = batch.get(i);

            if (sr.isSuccessful()) {
                markSent(outbox);
                sent++;
            } else {
                int result = handleFailure(outbox, sr);
                if (result == 1) invalidToken++;
                else if (result == 2) maxRetry++;
            }
        }

        outboxRepository.saveAll(batch);
        log.info("FCM 배치 결과: 성공={}/{}", sent, batch.size());
        return new PollResult(sent, invalidToken, maxRetry);
    }

    /**
     * FCM 배치 전송 자체가 실패(네트워크/서킷오픈)한 경우 PROCESSING → PENDING 즉시 복구.
     * 앱 크래시의 경우는 sweeper(OutboxPoller.sweepStuckProcessing)가 처리한다.
     */
    @Transactional
    public void resetBatchToPending(List<NotificationOutbox> batch) {
        for (NotificationOutbox outbox : batch) {
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setProcessingStartedAt(null);
        }
        outboxRepository.saveAll(batch);
    }

    private void markSent(NotificationOutbox outbox) {
        outbox.setStatus(OutboxStatus.SENT);
        outbox.setProcessedAt(LocalDateTime.now());
    }

    /**
     * @return 1=invalid token, 2=max retry exceeded, 0=retry scheduled
     */
    private int handleFailure(NotificationOutbox outbox, SendResponse sr) {
        MessagingErrorCode errorCode = sr.getException().getMessagingErrorCode();
        String reason = sr.getException().getMessage();

        if (errorCode == MessagingErrorCode.UNREGISTERED
                || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            outbox.setStatus(OutboxStatus.FAILED);
            outbox.setFailureReason("INVALID_TOKEN: " + reason);
            outbox.setProcessedAt(LocalDateTime.now());
            invalidateUserToken(outbox.getUserId());
            log.warn("FCM 유효하지 않은 토큰 처리: userId={}", outbox.getUserId());
            return 1;
        }

        int nextRetryCount = outbox.getRetryCount() + 1;
        if (nextRetryCount >= MAX_RETRY_COUNT) {
            outbox.setStatus(OutboxStatus.FAILED);
            outbox.setFailureReason("MAX_RETRY_EXCEEDED: " + reason);
            outbox.setProcessedAt(LocalDateTime.now());
            log.warn("FCM 최대 재시도 초과: outboxId={}", outbox.getId());
            return 2;
        }

        long backoffSeconds = (long) Math.pow(2, nextRetryCount);
        outbox.setRetryCount(nextRetryCount);
        outbox.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setFailureReason(reason);
        outbox.setProcessingStartedAt(null);
        log.debug("FCM 재시도 예약: outboxId={}, {}초 후", outbox.getId(), backoffSeconds);
        return 0;
    }

    private void invalidateUserToken(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFcmToken(null);
            userRepository.save(user);
        });
    }

    public record PollResult(int sent, int invalidTokenFailed, int maxRetryFailed) {}
}
