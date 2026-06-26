package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.notification.entity.OutboxStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxStatus 열거형 값 검증.
 */
class NotiTest {

    @Test
    void outboxStatus_PROCESSING_값존재() {
        assertThat(OutboxStatus.values())
                .contains(OutboxStatus.PROCESSING);
    }

    @Test
    void outboxStatus_전체값_확인() {
        assertThat(OutboxStatus.values())
                .containsExactlyInAnyOrder(
                        OutboxStatus.PENDING,
                        OutboxStatus.PROCESSING,
                        OutboxStatus.SENT,
                        OutboxStatus.FAILED
                );
    }
}
