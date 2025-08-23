package com.goodda.jejuday.auth.service.impl;

import com.goodda.jejuday.auth.entity.EmailVerification;
import com.goodda.jejuday.auth.entity.TemporaryUser;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.EmailVerificationRepository;
import com.goodda.jejuday.auth.repository.TemporaryUserRepository;
import com.goodda.jejuday.auth.service.EmailVerificationService;
import com.goodda.jejuday.common.exception.EmailSendingException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final TemporaryUserRepository temporaryUserRepository;

    @Override
    public void deleteVerificationByTemporaryUserEmail(String email) {
        emailVerificationRepository.findByTemporaryUser_Email(email).ifPresent(emailVerificationRepository::delete);
    }

    @Override
    public void deleteVerificationByUserEmail(String email) {
        emailVerificationRepository.findByUser_Email(email).ifPresent(emailVerificationRepository::delete);
    }

    @Override
    @Transactional
    public void saveVerificationForTemporaryUser(TemporaryUser temporaryUser, String code) {
        EmailVerification emailVerification = EmailVerification.builder()
                .temporaryUser(temporaryUser)
                .verificationCode(code)
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build();
        emailVerificationRepository.save(emailVerification);
    }

    @Override
    @Transactional
    public void saveVerificationForUser(User user, String code) {
        EmailVerification emailVerification = EmailVerification.builder()
                .user(user)
                .verificationCode(code)
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build();
        emailVerificationRepository.save(emailVerification);
    }

    @Override
    public boolean verifyTemporaryUserCode(String email, String code) {
        TemporaryUser temporaryUser = temporaryUserRepository.findByEmail(email)
                .orElseThrow(() -> new EmailSendingException("임시 사용자가 존재하지 않습니다."));

        EmailVerification emailVerification = emailVerificationRepository.findTopByTemporaryUserAndIsVerifiedFalseOrderByCreatedAtDesc(
                        temporaryUser)
                .orElseThrow(() -> new EmailSendingException("인증 기록이 없습니다."));

        if (emailVerification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new EmailSendingException("인증 코드가 만료되었습니다.");
        }

        if (!emailVerification.getVerificationCode().equals(code)) {
            throw new EmailSendingException("인증 코드가 일치하지 않습니다.");
        }

        emailVerification.setVerified(true);
        emailVerificationRepository.save(emailVerification);
        return true;
    }

    @Override
    public boolean verifyUserCode(String email, String code) {
        EmailVerification verification = emailVerificationRepository.findByUser_Email(email)
                .orElseThrow(() -> new EmailSendingException("인증 기록이 없습니다."));

        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new EmailSendingException("인증 코드가 만료되었습니다.");
        }

        if (!verification.getVerificationCode().equals(code)) {
            throw new EmailSendingException("인증 코드가 일치하지 않습니다.");
        }

        emailVerificationRepository.delete(verification);
        return true;
    }

    @Override
    public boolean isTemporaryUserVerified(String email) {
        TemporaryUser temporaryUser = temporaryUserRepository.findByEmail(email)
                .orElseThrow(() -> new EmailSendingException("임시 사용자가 존재하지 않습니다."));

        return emailVerificationRepository.findTopByTemporaryUserAndIsVerifiedTrueOrderByCreatedAtDesc(temporaryUser)
                .isPresent();
    }

    // ================================
    // 새로운 회원가입 플로우를 위한 메서드들
    // ================================

    /**
     * 회원가입용 이메일 인증 코드 검증 (임시 사용자 없이)
     */
    @Override
    public boolean verifyEmailCodeForRegistration(String email, String code) {
        EmailVerification emailVerification = emailVerificationRepository.findTopByEmailAndIsVerifiedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new EmailSendingException("인증 기록이 없습니다."));

        if (emailVerification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new EmailSendingException("인증 코드가 만료되었습니다.");
        }

        if (!emailVerification.getVerificationCode().equals(code)) {
            throw new EmailSendingException("인증 코드가 일치하지 않습니다.");
        }

        emailVerification.setVerified(true);
        emailVerificationRepository.save(emailVerification);
        return true;
    }

    /**
     * 회원가입용 이메일 인증 완료 여부 확인
     */
    @Override
    public boolean isEmailVerifiedForRegistration(String email) {
        return emailVerificationRepository.findTopByEmailAndIsVerifiedTrueOrderByCreatedAtDesc(email)
                .isPresent();
    }

    /**
     * 회원가입용 이메일 인증 정보 저장 (임시 사용자 없이)
     */
    @Override
    @Transactional
    public void saveEmailVerificationForRegistration(String email, String code) {
        // 기존 미인증 인증코드가 있다면 삭제
        List<EmailVerification> existingVerifications = emailVerificationRepository.findByEmailAndIsVerifiedFalse(email);
        if (!existingVerifications.isEmpty()) {
            emailVerificationRepository.deleteAll(existingVerifications);
        }

        EmailVerification emailVerification = EmailVerification.builder()
                .email(email)
                .verificationCode(code)
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(3))
                .build();
        emailVerificationRepository.save(emailVerification);
    }
}
