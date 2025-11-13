package com.goodda.jejuday.auth.service.impl;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.service.ReferralService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralServiceImpl implements ReferralService {

    private final UserRepository userRepository;

    // 보너스 상수 정의
    private static final int REFERRAL_BONUS = 500; // 추천인/피추천인 보너스
    private static final int WELCOME_BONUS = 300;  // 기본 환영 보너스
    private static final String WELCOME_CODE = "제주데이"; // 기본 보너스 코드

    @Override
    @Transactional
    public void processSignupBonus(Long newUserId, String referrerNickname) {
        if (referrerNickname == null || referrerNickname.trim().isEmpty()) {
            return;
        }

        // 신규 사용자 조회
        User newUser = userRepository.findById(newUserId)
                .orElseThrow(() -> new IllegalArgumentException("신규 사용자를 찾을 수 없습니다."));

        // 이미 가입 보너스를 받은 경우 처리하지 않음
        if (newUser.isReceivedSignupBonus()) {
            log.warn("사용자 {}는 이미 가입 보너스를 수령했습니다.", newUserId);
            return;
        }

        String trimmedNickname = referrerNickname.trim();

        // "제주데이" 입력 시 기본 환영 보너스 지급
        if (WELCOME_CODE.equals(trimmedNickname)) {
            giveWelcomeBonus(newUser);
            return;
        }

        // 추천인 닉네임으로 사용자 조회
        User referrer = userRepository.findByNickname(trimmedNickname)
                .orElse(null);

        if (referrer == null) {
            log.warn("추천인을 찾을 수 없습니다: {}", trimmedNickname);
            throw new IllegalArgumentException("존재하지 않는 추천인입니다.");
        }

        // 자기 자신을 추천인으로 등록하는 경우 방지
        if (referrer.getId().equals(newUserId)) {
            log.warn("자기 자신을 추천인으로 등록하려고 시도: {}", newUserId);
            throw new IllegalArgumentException("자기 자신을 추천인으로 등록할 수 없습니다.");
        }

        // 추천인 정보 설정 및 보너스 지급
        giveReferralBonus(newUser, referrer);

        log.info("추천 보너스 지급 완료 - 신규사용자: {}, 추천인: {}, 보너스: {}한라봉",
                newUser.getNickname(), referrer.getNickname(), REFERRAL_BONUS);
    }

    @Override
    @Transactional(readOnly = true)
    public int getTotalReferrals(Long userId) {
        return userRepository.findById(userId)
                .map(User::getTotalReferrals)
                .orElse(0);
    }

    /**
     * 환영 보너스 지급 ("제주데이" 입력 시)
     */
    private void giveWelcomeBonus(User user) {
        user.setHallabong(user.getHallabong() + WELCOME_BONUS);
        user.setReceivedSignupBonus(true);
        user.setBonusType("WELCOME");
        userRepository.save(user);

        log.info("환영 보너스 지급 완료 - 사용자: {}, 보너스: {}한라봉",
                user.getNickname(), WELCOME_BONUS);
    }

    /**
     * 추천인과 피추천인에게 보너스 지급
     */
    private void giveReferralBonus(User newUser, User referrer) {
        // 신규 사용자에게 보너스 지급 및 추천인 설정
        newUser.setHallabong(newUser.getHallabong() + REFERRAL_BONUS);
        newUser.setReferrerId(referrer.getId());
        newUser.setReceivedSignupBonus(true);
        newUser.setBonusType("REFERRAL");

        // 추천인에게 보너스 지급 및 추천 수 증가
        referrer.setHallabong(referrer.getHallabong() + REFERRAL_BONUS);
        referrer.setTotalReferrals(referrer.getTotalReferrals() + 1);

        // 데이터베이스에 저장
        userRepository.save(newUser);
        userRepository.save(referrer);
    }
}
