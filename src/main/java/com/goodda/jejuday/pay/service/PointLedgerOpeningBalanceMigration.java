package com.goodda.jejuday.pay.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원장 도입 이전부터 존재하던 users.hallabong 잔액을 OPENING_BALANCE 원장 1건으로 백필한다.
 * 이게 없으면 이미 잔액을 보유한 기존 사용자는 SUM(ledger) != hallabong 상태로 시작하게 되어
 * PointLedgerReconciliationJob이 첫날부터 전원 드리프트로 오탐한다.
 *
 * <p>기본적으로 비활성화 — 원장 도입 배포 시 한 번 point-ledger.migrate-opening-balance=true로
 * 켰다가 마이그레이션 로그(백필 건수)를 확인한 뒤 다시 끈다. NOT EXISTS로 이미 백필된 사용자는
 * 건너뛰므로(멱등 키 = userId:OPENING_BALANCE) 여러 번 켜져도 안전하지만, 매 기동마다 불필요한
 * INSERT..SELECT를 도는 걸 막기 위해 기본값은 off로 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "point-ledger.migrate-opening-balance", havingValue = "true")
public class PointLedgerOpeningBalanceMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int backfilled = jdbcTemplate.update("""
                INSERT IGNORE INTO point_ledger (user_id, amount, reason, ref_id, idempotency_key, created_at)
                SELECT u.user_id, u.hallabong, 'OPENING_BALANCE', NULL,
                       CONCAT(u.user_id, ':OPENING_BALANCE'), NOW()
                FROM users u
                WHERE NOT EXISTS (
                    SELECT 1 FROM point_ledger l WHERE l.idempotency_key = CONCAT(u.user_id, ':OPENING_BALANCE')
                )
                """);

        log.info("포인트 원장 OPENING_BALANCE 마이그레이션 완료: 백필된 사용자 수={}", backfilled);
    }
}
