package com.goodda.jejuday.pay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 한라봉 잔액 변동의 정본(source of truth). users.hallabong은 이 원장의 파생값이다.
 * idempotency_key 유니크 제약이 재시도/동시요청을 흡수하고, 원장 insert와 잔액 UPDATE가
 * 같은 트랜잭션으로 묶여 원자성을 보장한다 — PointLedgerService.record 참고.
 */
@Entity
@Table(name = "point_ledger",
        uniqueConstraints = @UniqueConstraint(name = "uk_point_ledger_idempotency_key", columnNames = "idempotency_key"),
        indexes = @Index(name = "idx_point_ledger_user_id", columnList = "user_id"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int amount; // ±

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LedgerReason reason;

    @Column(name = "ref_id")
    private Long refId; // 참조 대상 (상품교환 id, 챌린지 id 등)

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
