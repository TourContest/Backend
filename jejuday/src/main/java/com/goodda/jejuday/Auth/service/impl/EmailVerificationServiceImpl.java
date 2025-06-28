package com.goodda.jejuday.Auth.service.impl;

import com.goodda.jejuday.Auth.entity.EmailVerification;
import com.goodda.jejuday.Auth.entity.TemporaryUser;
import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Auth.repository.EmailVerificationRepository;
import com.goodda.jejuday.Auth.repository.TemporaryUserRepository;
import com.goodda.jejuday.Auth.service.EmailVerificationService;
import com.goodda.jejuday.Auth.util.exception.EmailSendingException;
import java.time.LocalDateTime;
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
}
