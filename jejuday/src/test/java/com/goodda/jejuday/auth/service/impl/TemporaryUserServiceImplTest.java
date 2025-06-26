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
import com.goodda.jejuday.auth.util.exception.DuplicateEmailException;
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
    private TemporaryUserServiceImpl temporaryUserService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        temporaryUserRepository = mock(TemporaryUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        temporaryUserService = new TemporaryUserServiceImpl(temporaryUserRepository, passwordEncoder);
    }

    @Test
    void 임시저장_테스트() {
        //  given
        Language language = Language.KOREAN;
        Platform platform = Platform.APP;
        String name = "tester";
        String email = "test@naver.com";
        String rawPassword = "testpassword";
        String encodedPassword = "encodedPassword123";

        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

        //  when
        temporaryUserService.save(language, platform, name, email, rawPassword);

        //  then
        ArgumentCaptor<TemporaryUser> captor = ArgumentCaptor.forClass(TemporaryUser.class);
        verify(temporaryUserRepository, times(1)).save(captor.capture());

        TemporaryUser temporaryUser = captor.getValue();

        assertEquals(name, temporaryUser.getName());
        assertEquals(email, temporaryUser.getEmail());
        assertEquals(encodedPassword, temporaryUser.getPassword());
    }

    @Test
    @DisplayName("이메일 중복으로 인한 예외")
    void 임시저장_예외() {
        //  given
        Language language = Language.KOREAN;
        Platform platform = Platform.APP;
        String name = "tester";
        String email = "test@naver.com";
        String password = "testpassword";

        //  when
        when(temporaryUserRepository.existsByEmail(email)).thenReturn(true);

        //  then
        DuplicateEmailException exception = assertThrows(DuplicateEmailException.class,
                () -> temporaryUserService.save(language, platform, name, email, password));

        assertEquals("이미 존재하는 이메일입니다.", exception.getMessage());

        verify(temporaryUserRepository, times(0)).save(any());
    }

    @Test
    void 이메일_찾기() {
        //  given
        String email = "test@naver.com";
        TemporaryUser temporaryUser = TemporaryUser.builder().email(email).build();
        when(temporaryUserRepository.findByEmail(email)).thenReturn(Optional.of(temporaryUser));

        //  when
        Optional<TemporaryUser> result = temporaryUserService.findByEmail(email);

        //  then
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
        // given
        LocalDateTime cutoff = LocalDateTime.of(2025, 1, 1, 12, 0);  // 고정된 시간
        TemporaryUser mockUser = TemporaryUser.builder().email("old@test.com").build();
        when(temporaryUserRepository.findByCreatedAtBefore(cutoff)).thenReturn(List.of(mockUser));

        // when
        List<TemporaryUser> result = temporaryUserService.findByCreatedAtBefore(cutoff);

        // then
        assertEquals(1, result.size());
        assertEquals("old@test.com", result.get(0).getEmail());
    }
}