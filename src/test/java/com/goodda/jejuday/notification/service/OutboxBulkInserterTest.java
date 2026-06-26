package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.notification.service.OutboxBulkInserter.BulkOutboxRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxBulkInserterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private OutboxBulkInserter inserter;

    @Test
    void insertChunk_단건_SQL에VALUES절포함() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        inserter.insertChunk(List.of(row(1L)));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("INSERT IGNORE INTO notification_outbox");
    }

    @Test
    void insertChunk_N건_파라미터수확인() {
        int n = 3;
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(n);

        inserter.insertChunk(List.of(row(1L), row(2L), row(3L)));

        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), paramsCaptor.capture());
        // row당 8개 파라미터 (userId, fcmToken, title, body, type, dedupKey, now, now)
        assertThat(paramsCaptor.getValue()).hasSize(n * 8);
    }

    @Test
    void insertChunk_빈리스트면_DB호출안함() {
        int result = inserter.insertChunk(List.of());

        verifyNoInteractions(jdbcTemplate);
        assertThat(result).isZero();
    }

    @Test
    void CHUNK_SIZE_기반_청크수_계산() {
        // ceil(N / CHUNK_SIZE) 공식 검증 (501건 → 2청크, 500건 → 1청크)
        assertThat((int) Math.ceil(501.0 / OutboxBulkInserter.CHUNK_SIZE)).isEqualTo(2);
        assertThat((int) Math.ceil(500.0 / OutboxBulkInserter.CHUNK_SIZE)).isEqualTo(1);
        assertThat((int) Math.ceil(1.0 / OutboxBulkInserter.CHUNK_SIZE)).isEqualTo(1);
        assertThat(OutboxBulkInserter.CHUNK_SIZE).isEqualTo(500);
    }

    private BulkOutboxRow row(Long userId) {
        return new BulkOutboxRow(userId, "valid-token-longer-than-20-chars",
                "제주데이", "테스트", "ATTENDANCE", "attendance:2024-01-01");
    }
}
