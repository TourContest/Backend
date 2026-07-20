package com.goodda.jejuday.pay.repository;

import com.goodda.jejuday.pay.entity.PointLedger;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointLedgerRepository extends JpaRepository<PointLedger, Long> {

    /**
     * 멱등 INSERT. idempotency_key 중복 시 조용히 0행 — 유니크 제약 위반 예외로 받으면
     * 같은 트랜잭션이 rollback-only로 마킹되어 이후 로직이 전부 죽으므로 IGNORE로 흡수한다.
     */
    @Modifying
    @Query(value = "INSERT IGNORE INTO point_ledger (user_id, amount, reason, ref_id, idempotency_key, created_at) "
            + "VALUES (:userId, :amount, :reason, :refId, :idemKey, NOW())", nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("amount") int amount, @Param("reason") String reason,
            @Param("refId") Long refId, @Param("idemKey") String idemKey);

    /** 보정 배치용 — 사용자별 원장 합계를 GROUP BY로 일괄 조회 */
    @Query("SELECT l.userId, SUM(l.amount) FROM PointLedger l GROUP BY l.userId")
    List<Object[]> sumAmountGroupByAllUsers();

    boolean existsByIdempotencyKey(String idempotencyKey);
}
