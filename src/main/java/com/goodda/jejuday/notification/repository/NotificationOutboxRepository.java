package com.goodda.jejuday.notification.repository;

import com.goodda.jejuday.notification.entity.NotificationOutbox;
import com.goodda.jejuday.notification.entity.NotificationOutbox.OutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    @Query("""
            SELECT o FROM NotificationOutbox o
            WHERE o.status = 'PENDING'
              AND o.nextRetryAt <= :now
            ORDER BY o.createdAt ASC
            """)
    List<NotificationOutbox> findPendingEntries(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("""
            DELETE FROM NotificationOutbox o
            WHERE o.status IN ('DONE', 'DEAD')
              AND o.processedAt < :cutoff
            """)
    int deleteCompleted(@Param("cutoff") LocalDateTime cutoff);

    long countByStatus(OutboxStatus status);
}
