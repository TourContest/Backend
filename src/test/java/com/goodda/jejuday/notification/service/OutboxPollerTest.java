package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.notification.entity.NotificationOutbox;
import com.goodda.jejuday.notification.entity.NotificationType;
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
import java.util.stream.IntStream;

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

    private List<NotificationOutbox> fullBatch() {
        return IntStream.rangeClosed(1, NotificationOutboxRepository.PENDING_BATCH_SIZE)
                .mapToObj(i -> buildOutbox((long) i)).toList();
    }

    @Test
    void poll_배치가_꽉차있으면_대기없이_연속으로_가져간다() throws Exception {
        // 꽉 찬 배치 2번 → 덜 찬 배치 1번에서 종료: claim/전송/반영이 각 3회
        when(txHelper.claimBatch()).thenReturn(fullBatch(), fullBatch(), List.of(buildOutbox(9999L)));
        when(fcmGateway.sendEach(anyList())).thenReturn(batchResponse);
        when(txHelper.applyResults(anyList(), any())).thenReturn(new PollResult(0, 0, 0));

        poller.poll();

        verify(txHelper, times(3)).claimBatch();
        verify(fcmGateway, times(3)).sendEach(anyList());
        verify(txHelper, times(3)).applyResults(anyList(), any());
    }

    @Test
    void poll_배치가_덜차있으면_한번만_가져간다() throws Exception {
        when(txHelper.claimBatch()).thenReturn(List.of(buildOutbox(1L)));
        when(fcmGateway.sendEach(anyList())).thenReturn(batchResponse);
        when(txHelper.applyResults(anyList(), any())).thenReturn(new PollResult(1, 0, 0));

        poller.poll();

        verify(txHelper, times(1)).claimBatch();
    }

    @Test
    void poll_꽉찬배치여도_FCM실패시_루프를_멈춘다() throws Exception {
        // FCM 장애 시 PENDING 으로 되돌린 뒤 곧바로 다시 가져가면 빈 루프가 되므로 다음 주기까지 쉰다
        when(txHelper.claimBatch()).thenReturn(fullBatch());
        when(fcmGateway.sendEach(anyList())).thenThrow(new RuntimeException("network error"));

        poller.poll();

        verify(txHelper, times(1)).claimBatch();
        verify(txHelper).resetBatchToPending(anyList());
    }

    private NotificationOutbox buildOutbox(Long id) {
        return NotificationOutbox.builder()
                .id(id)
                .userId(10L)
                .fcmToken("valid-token-longer-than-20-chars")
                .title("제주데이")
                .body("테스트 메시지")
                .type(NotificationType.ATTENDANCE)   // toFcmMessage 가 type/dedupKey 를 data 로 싣는다
                .dedupKey("attendance:2026-01-01")
                .status(OutboxStatus.PROCESSING)
                .retryCount(0)
                .nextRetryAt(LocalDateTime.now())
                .build();
    }
}
