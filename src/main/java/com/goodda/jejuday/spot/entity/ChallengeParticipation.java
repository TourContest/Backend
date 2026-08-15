package com.goodda.jejuday.spot.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "challenge_participation",
        uniqueConstraints = @UniqueConstraint(name = "uk_challenge_user", columnNames = {"challenge_id","user_id"}),
        indexes = {
                @Index(name = "ix_cp_user", columnList = "user_id"),
                @Index(name = "ix_cp_challenge", columnList = "challenge_id"),
                @Index(name = "ix_cp_status", columnList = "status")
        }
)
// 챌린지 장소와 유저의 중계 테이블
// 진행중(JOINED/SUBMITTED/APPROVED) 참여는 ChallengeActionService.cancel()로 취소 가능(-> CANCELLED)
@Getter @Setter
public class ChallengeParticipation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Spot.type = CHALLENGE(UGC 승격) 또는 SPOT(공공데이터 추천) 둘 다 참조 가능
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Spot challenge;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 유저별 진행 기간/상태
    @Column(name = "start_date") private LocalDate startDate;  // 가입일(선택)
    @Column(name = "end_date")   private LocalDate endDate;    // 목표/마감일(선택)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.JOINED;

    @Column(name = "proof_url", length = 512)
    private String proofUrl;

    @CreationTimestamp @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    public enum Status { JOINED, SUBMITTED, APPROVED, COMPLETED, REJECTED, CANCELLED }
}