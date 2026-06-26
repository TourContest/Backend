package com.goodda.jejuday.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * notification_outbox 벌크 INSERT 전담 컴포넌트.
 *
 * <p>단건 INSERT × N 대신 멀티-row VALUES 절 한 번으로 처리한다.
 * REQUIRES_NEW로 호출 측 트랜잭션과 격리되어 청크 단위 독립 커밋이 보장된다.
 * ON CONFLICT DO NOTHING으로 멱등성을 보장하므로 재실행해도 중복 삽입 없음.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxBulkInserter {

    static final int CHUNK_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @return 실제 삽입된 row 수 (충돌로 무시된 row 제외)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int insertChunk(List<BulkOutboxRow> rows) {
        if (rows.isEmpty()) return 0;

        StringBuilder sql = new StringBuilder("""
                INSERT IGNORE INTO notification_outbox
                    (user_id, fcm_token, title, body, type, dedup_key, status, retry_count, next_retry_at, created_at)
                VALUES
                """);

        LocalDateTime now = LocalDateTime.now();
        List<Object> params = new ArrayList<>(rows.size() * 8);

        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("(?,?,?,?,?,?,'PENDING',0,?,?)");
            BulkOutboxRow r = rows.get(i);
            params.add(r.userId());
            params.add(r.fcmToken());
            params.add(r.title());
            params.add(r.body());
            params.add(r.type());
            params.add(r.dedupKey());
            params.add(now);
            params.add(now);
        }
        // INSERT IGNORE 로 중복(유니크 제약 위반)을 무시 — MySQL 멱등 INSERT

        int inserted = jdbcTemplate.update(sql.toString(), params.toArray());
        log.debug("벌크 INSERT: 요청={}, 삽입={}", rows.size(), inserted);
        return inserted;
    }

    public record BulkOutboxRow(
            Long userId,
            String fcmToken,
            String title,
            String body,
            String type,
            String dedupKey
    ) {}
}
