package com.goodda.jejuday.notification.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;

// 멱등이어야 하는 알림(걸음수 목표, 승격)만 dedup_key 를 채운다. NULL 은 유니크에서 제외되므로
// 댓글/답글/댓글 좋아요처럼 같은 context_key 가 의도적으로 반복되는 알림은 기존대로 계속 쌓인다.
@Table(name = "notification_entity",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_dedup", columnNames = {"user_id", "type", "dedup_key"}))
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    @ColumnDefault("false")
    private boolean isRead;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "target_token")
    private String targetToken;

    // 어느 게시글/댓글에 대한 알림인지 식별하는 키 (예: "post:123:reply", "comment:45")
    @Column(name = "context_key")
    private String contextKey;

    // 멱등 알림 식별 키 (NULL = 중복 방지 대상 아님). uk_notification_dedup 참고
    @Column(name = "dedup_key")
    private String dedupKey;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
