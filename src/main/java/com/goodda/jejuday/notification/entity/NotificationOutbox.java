package com.goodda.jejuday.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "notification_outbox",
        indexes = {
                @Index(name = "idx_outbox_status_retry", columnList = "status, nextRetryAt"),
                @Index(name = "idx_outbox_user", columnList = "userId")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String contextKey;

    @Column
    private String targetToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column
    private LocalDateTime nextRetryAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime processedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        nextRetryAt = LocalDateTime.now();
    }

    public void markProcessing() {
        this.status = OutboxStatus.PROCESSING;
    }

    public void markDone() {
        this.status = OutboxStatus.DONE;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.retryCount++;
        this.errorMessage = error;
        if (retryCount >= 3) {
            this.status = OutboxStatus.DEAD;
        } else {
            this.status = OutboxStatus.PENDING;
            // 지수 백오프: 1분, 2분, 4분
            this.nextRetryAt = LocalDateTime.now().plusMinutes(1L << (retryCount - 1));
        }
    }

    public enum OutboxStatus {
        PENDING, PROCESSING, DONE, DEAD
    }
}
