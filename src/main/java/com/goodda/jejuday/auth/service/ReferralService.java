package com.goodda.jejuday.auth.service;

public interface ReferralService {

    /**
     * 가입 보너스 처리 (추천인 또는 제주데이 보너스)
     * @param newUserId 신규 가입 사용자 ID
     * @param referrerNickname 추천인 닉네임 또는 "제주데이"
     */
    void processSignupBonus(Long newUserId, String referrerNickname);

    /**
     * 사용자의 총 추천 수 조회
     * @param userId 사용자 ID
     * @return 총 추천 수
     */
    int getTotalReferrals(Long userId);
}