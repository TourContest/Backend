package com.goodda.jejuday.pay.service;

import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.common.exception.InsufficientHallabongException;
import com.goodda.jejuday.pay.entity.LedgerReason;
import com.goodda.jejuday.pay.repository.PointLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모든 한라봉 잔액 변동은 이 서비스를 경유한다. 원장이 정본, users.hallabong은 파생값.
 * idempotencyKey 유니크 제약이 재시도를 흡수하고, 원장 insert + 잔액 UPDATE가 한 트랜잭션으로
 * 묶여 원자성을 보장한다.
 *
 * <p>잔액 조회 전략: 매 조회마다 SUM(point_ledger)을 계산하는 이벤트 소싱형 잔액 대신
 * users.hallabong을 캐시 컬럼으로 유지한다. 잔액 조회는 조회수 대비 압도적으로 빈번한 반면
 * (포인트 화면, 상품 구매 가능 여부 판단 등) 매번 SUM은 원장이 커질수록 비용이 커진다.
 * 캐시가 정본과 어긋날 위험은 (1) 모든 쓰기가 record() 한 경로로만 나가고 (2) 매일 밤
 * PointLedgerReconciliationJob이 SUM과 대조해 교정하는 것으로 상쇄한다 — 즉 "쓰기는 원장이
 * 정본, 읽기는 캐시가 빠른 경로"라는 절충이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointLedgerService {

    private final PointLedgerRepository ledgerRepository;
    private final UserRepository userRepository;

    /**
     * @param amount  적립이면 양수, 차감이면 음수
     * @param idemKey 호출부별 자연 키 (예: userId:ATTENDANCE:date) — 같은 키로 재호출하면 아무 일도
     *                일어나지 않는다
     * @return 실제로 잔액에 반영됐으면 true, 멱등 키 중복(이미 반영됨)이면 false
     * @throws InsufficientHallabongException 차감인데 잔액이 부족한 경우 — 원장 insert도 함께 롤백된다
     */
    @Transactional
    public boolean record(Long userId, int amount, LedgerReason reason, Long refId, String idemKey) {
        int inserted = ledgerRepository.insertIgnore(userId, amount, reason.name(), refId, idemKey);
        if (inserted == 0) {
            log.debug("원장 기록 중복 무시: userId={}, reason={}, idemKey={}", userId, reason, idemKey);
            return false;
        }

        int updated = (amount >= 0)
                ? userRepository.incrementHallabong(userId, amount)
                : userRepository.decrementHallabong(userId, -amount);
        if (updated == 0) {
            throw new InsufficientHallabongException("한라봉 포인트 부족");
        }
        return true;
    }
}
