package com.goodda.jejuday.auth.service;

import com.goodda.jejuday.auth.entity.TemporaryUser;
import com.goodda.jejuday.auth.entity.User;

public interface EmailVerificationService {
    void deleteVerificationByTemporaryUserEmail(String email);

    void deleteVerificationByUserEmail(String email);

    void saveVerificationForTemporaryUser(TemporaryUser temporaryUser, String code);

    void saveVerificationForUser(User user, String code);

    boolean verifyTemporaryUserCode(String email, String code);

    boolean verifyUserCode(String email, String code);

    boolean isTemporaryUserVerified(String email);

    // ================================
    // 새로운 회원가입 플로우를 위한 메서드들
    // ================================

    /**
     * 회원가입용 이메일 인증 코드 검증 (임시 사용자 없이)
     */
    boolean verifyEmailCodeForRegistration(String email, String code);

    /**
     * 회원가입용 이메일 인증 완료 여부 확인
     */
    boolean isEmailVerifiedForRegistration(String email);

    /**
     * 회원가입용 이메일 인증 정보 저장 (임시 사용자 없이)
     */
    void saveEmailVerificationForRegistration(String email, String code);
}
