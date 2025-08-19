package com.goodda.jejuday.spot.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "challenge_participation",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_challenge_user", columnNames = {"challenge_id","user_id"}
    ),
    indexes = {
        @Index(name = "ix_cp_user", columnList = "user_id"),
        @Index(name = "ix_cp_challenge", columnList = "challenge_id")
    }
)
// TODO : 고민중인 항목 - 챌린지 진행중인 항목이 있다면 그 항목에 대해서는 어떻게 처리 할건지.
// 챌린지 장소와 유저의 중계 테이블
@Getter @Setter
public class ChallengeParticipation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 반드시 Spot.type = CHALLENGE 여야 함 (서비스 레벨에서 검증)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Spot challenge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.JOINED;

    @Column(name = "progress", columnDefinition = "INT DEFAULT 0")
    private Integer progress = 0;     // (선택) 진행률

    // (선택) 인증용 자료 링크/이미지 경로 등
    @Column(name = "proof_url")
    private String proofUrl;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 진행전 -> 진행중 -> 승인대기 -> 1. 완료 2. 취소
    public enum Status {
        JOINED, SUBMITTED, APPROVED, REJECTED, COMPLETED, CANCELLED
    }
}