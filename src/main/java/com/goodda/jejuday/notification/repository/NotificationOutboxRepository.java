package com.goodda.jejuday.notification.repository;

import com.goodda.jejuday.notification.entity.NotificationOutbox;
import com.goodda.jejuday.notification.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    // 재시도 준비된 row를 우선 처리하도록 next_retry_at 기준으로 정렬
    @Query(value = """
        SELECT * FROM notification_outbox
        WHERE status = 'PENDING'
          AND next_retry_at <= now()
        ORDER BY next_retry_at, created_at
        LIMIT 100
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<NotificationOutbox> findPendingBatch();

    long countByStatus(OutboxStatus status);

    // TX1이 크래시로 TX2를 완료하지 못한 PROCESSING row를 복구
    @Modifying
    @Query(value = """
        UPDATE notification_outbox
        SET status = 'PENDING', processing_started_at = NULL
        WHERE status = 'PROCESSING'
          AND processing_started_at < :threshold
        """, nativeQuery = true)
    int resetStuckProcessing(@Param("threshold") LocalDateTime threshold);

    // Java 클럭 기준 통일: now()를 SQL에서 제거하고 파라미터로 전달
    // INSERT IGNORE로 (user_id, type, dedup_key) 유니크 중복 무시 — MySQL 멱등 INSERT
    @Modifying
    @Query(value = """
        INSERT IGNORE INTO notification_outbox
            (user_id, fcm_token, title, body, type, dedup_key, status, retry_count, next_retry_at, created_at)
        VALUES
            (:userId, :fcmToken, :title, :body, :type, :dedupKey, 'PENDING', 0, :now, :now)
        """, nativeQuery = true)
    void insertIfNotDuplicate(
        @Param("userId") Long userId,
        @Param("fcmToken") String fcmToken,
        @Param("title") String title,
        @Param("body") String body,
        @Param("type") String type,
        @Param("dedupKey") String dedupKey,
        @Param("now") LocalDateTime now
    );

    // 보존 기간이 지난 SENT/FAILED row 일괄 삭제 (OutboxCleanupScheduler 전용)
    @Modifying
    @Query(value = """
        DELETE FROM notification_outbox
        WHERE status IN ('SENT', 'FAILED')
          AND processed_at < :cutoff
        """, nativeQuery = true)
    int deleteOldTerminalRows(@Param("cutoff") LocalDateTime cutoff);
}
