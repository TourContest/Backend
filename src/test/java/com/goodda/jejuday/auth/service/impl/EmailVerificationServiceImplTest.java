package com.goodda.jejuday.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.auth.entity.EmailVerification;
import com.goodda.jejuday.auth.repository.EmailVerificationRepository;
import com.goodda.jejuday.auth.repository.TemporaryUserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailVerificationServiceImplTest {

    private EmailVerificationRepository emailVerificationRepository;
    private EmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        emailVerificationRepository = mock(EmailVerificationRepository.class);
        service = new EmailVerificationServiceImpl(
                emailVerificationRepository,
                mock(TemporaryUserRepository.class)
        );
    }

    @Test
    void registrationVerificationCodeIsValidForTenMinutes() {
        when(emailVerificationRepository.findByEmailAndIsVerifiedFalse("test@example.com"))
                .thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        service.saveEmailVerificationForRegistration("test@example.com", "123456");

        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository).save(captor.capture());
        LocalDateTime expiresAt = captor.getValue().getExpiresAt();
        assertFalse(expiresAt.isBefore(before.plusMinutes(10)));
        assertFalse(expiresAt.isAfter(LocalDateTime.now().plusMinutes(10)));
    }

    @Test
    void expiredRegistrationVerificationIsNotAccepted() {
        EmailVerification expired = EmailVerification.builder()
                .isVerified(true)
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();
        when(emailVerificationRepository.findTopByEmailAndIsVerifiedTrueOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.of(expired));

        assertFalse(service.isEmailVerifiedForRegistration("test@example.com"));
    }

    @Test
    void unexpiredRegistrationVerificationIsAccepted() {
        EmailVerification valid = EmailVerification.builder()
                .isVerified(true)
                .expiresAt(LocalDateTime.now().plusMinutes(1))
                .build();
        when(emailVerificationRepository.findTopByEmailAndIsVerifiedTrueOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.of(valid));

        assertTrue(service.isEmailVerifiedForRegistration("test@example.com"));
    }
}
