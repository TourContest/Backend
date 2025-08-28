package com.goodda.jejuday.steps.service;

import com.goodda.jejuday.auth.entity.User;

public interface DailyStartBonusService {
    /**
     * 전날 걸음수를 기반으로 시작 보너스 계산
     * @param user 사용자
     * @return 보너스 걸음수 (0 이상)
     */
    long calculateStartBonus(User user);

    /**
     * 시작 보너스를 적용
     * @param userId 사용자 ID
     * @return 적용된 보너스 걸음수
     */
    long applyStartBonus(Long userId);

    /**
     * 시작 보너스 적용 가능 여부 확인
     * @param userId 사용자 ID
     * @return 적용 가능 여부
     */
    boolean canApplyStartBonus(Long userId);
}
