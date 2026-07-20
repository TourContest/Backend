package com.goodda.jejuday.attendance.service;

import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.service.UserService;
import com.goodda.jejuday.pay.entity.LedgerReason;
import com.goodda.jejuday.pay.service.PointLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HallabongService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PointLedgerService pointLedgerService;

    /**
     * @param idemKey 호출부 책임의 자연 키 (예: 출석은 userId:ATTENDANCE:date) — 하루 중복 지급 방지
     */
    @Transactional
    public void addHallabong(Long userId, int amount, LedgerReason reason, Long refId, String idemKey) {
        pointLedgerService.record(userId, amount, reason, refId, idemKey);
    }

    /**
     * hallabong 원자적 차감.
     * 잔액 부족이면 IllegalStateException을 던진다.
     */
    @Transactional
    public void deductHallabong(Long userId, int amount) {
        int updated = userRepository.decrementHallabong(userId, amount);
        if (updated == 0) {
            throw new IllegalStateException("한라봉이 부족합니다.");
        }
    }

    public int getHallabong(Long userId) {
        return userService.getUserById(userId).getHallabong();
    }
}
