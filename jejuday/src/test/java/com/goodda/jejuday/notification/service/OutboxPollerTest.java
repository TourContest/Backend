package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.notification.entity.NotificationOutbox;
import com.goodda.jejuday.notification.entity.OutboxStatus;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.service.OutboxTransactionHelper.PollResult;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.Message;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private FcmGateway fcmGateway;
    @Mock
    private OutboxTransactionHelper txHelper;
    @Mock
    private BatchResponse batchResponse;

    private OutboxPoller poller;

    @BeforeEach
    void setUp() {
        poller = new OutboxPoller(outboxRepository, fcmGateway, txHelper, new SimpleMeterRegistry());
    }

    @Test
    void poll_빈배치면_FCM호출안함() {
        when(txHelper.claimBatch()).thenReturn(List.of());

        poller.poll();

        verifyNoInteractions(fcmGateway);
        verify(txHelper, never()).applyResults(anyList(), any());
    }

    @Test
    void poll_TX1_FCM_TX2_순서보장() throws Exception {
        NotificationOutbox outbox = buildOutbox(1L);
        when(txHelper.claimBatch()).thenReturn(List.of(outbox));
        when(fcmGateway.sendEach(anyList())).thenReturn(batchResponse);
        when(txHelper.applyResults(anyList(), any())).thenReturn(new PollResult(1, 0, 0));

        poller.poll();

        InOrder inOrder = inOrder(txHelper, fcmGateway);
        inOrder.verify(txHelper).claimBatch();          // TX1
        inOrder.verify(fcmGateway).sendEach(anyList()); // FCM (트랜잭션 밖)
        inOrder.verify(txHelper).applyResults(anyList(), any()); // TX2
    }

    @Test
    void poll_FCM실패시_PENDING즉시복구() throws Exception {
        NotificationOutbox outbox = buildOutbox(1L);
        when(txHelper.claimBatch()).thenReturn(List.of(outbox));
        when(fcmGateway.sendEach(anyList())).thenThrow(new RuntimeException("network error"));

        poller.poll();

        verify(txHelper).resetBatchToPending(anyList());
        verify(txHelper, never()).applyResults(anyList(), any());
    }

    @Test
    void sweepStuckProcessing_임계값이전row복구() {
        when(outboxRepository.resetStuckProcessing(any(LocalDateTime.class))).thenReturn(3);

        poller.sweepStuckProcessing();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxRepository).resetStuckProcessing(captor.capture());
        // threshold는 현재 시각 - 2분 근방이어야 한다
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void sweepStuckProcessing_복구건없으면_경고없음() {
        when(outboxRepository.resetStuckProcessing(any())).thenReturn(0);

        poller.sweepStuckProcessing(); // 예외 없이 종료되면 OK
    }

    private NotificationOutbox buildOutbox(Long id) {
        return NotificationOutbox.builder()
                .id(id)
                .userId(10L)
                .fcmToken("valid-token-longer-than-20-chars")
                .title("제주데이")
                .body("테스트 메시지")
                .status(OutboxStatus.PROCESSING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();
    }
}
