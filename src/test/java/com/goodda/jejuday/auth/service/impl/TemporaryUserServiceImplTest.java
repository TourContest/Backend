package com.goodda.jejuday.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.TemporaryUser;
import com.goodda.jejuday.auth.repository.TemporaryUserRepository;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.common.exception.DuplicateEmailException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class TemporaryUserServiceImplTest {

    private TemporaryUserRepository temporaryUserRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private TemporaryUserServiceImpl temporaryUserService;

    @BeforeEach
    void setUp() {
        temporaryUserRepository = mock(TemporaryUserRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);

        temporaryUserService = new TemporaryUserServiceImpl(
                temporaryUserRepository,
                userRepository,
                passwordEncoder
        );
    }

    @Test
    void 이메일_찾기() {
        String email = "test@naver.com";
        TemporaryUser temporaryUser = TemporaryUser.builder().email(email).build();
        when(temporaryUserRepository.findByEmail(email)).thenReturn(Optional.of(temporaryUser));

        Optional<TemporaryUser> result = temporaryUserService.findByEmail(email);

        assertTrue(result.isPresent());
        assertEquals(email, result.get().getEmail());
    }

    @Test
    void 이메일_중복여부() {
        String email = "test@naver.com";
        when(temporaryUserRepository.existsByEmail(email)).thenReturn(true);
        assertTrue(temporaryUserService.existsByEmail(email));
    }

    @Test
    void 이메일_삭제() {
        Long id = 12L;
        temporaryUserService.deleteByTemporaryUserId(id);
        verify(temporaryUserRepository).deleteByTemporaryUserId(id);
    }

    @Test
    void 시간보다_이전의_임시사용자_조회() {
        LocalDateTime cutoff = LocalDateTime.of(2025, 1, 1, 12, 0);
        TemporaryUser mockUser = TemporaryUser.builder().email("old@test.com").build();
        when(temporaryUserRepository.findByCreatedAtBefore(cutoff)).thenReturn(List.of(mockUser));

        List<TemporaryUser> result = temporaryUserService.findByCreatedAtBefore(cutoff);

        assertEquals(1, result.size());
        assertEquals("old@test.com", result.get(0).getEmail());
    }
}
