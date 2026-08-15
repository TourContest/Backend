package com.goodda.jejuday.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_block",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_block", columnNames = {"blocker_id", "blocked_id"}),
        indexes = {
                @Index(name = "ix_user_block_blocker", columnList = "blocker_id"),
                @Index(name = "ix_user_block_blocked", columnList = "blocked_id")
        }
)
@Getter @Setter
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 차단을 건 사람
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    // 차단당한 사람
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
