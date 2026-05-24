package com.goodda.jejuday.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.dto.login.request.LoginRequest;
import com.goodda.jejuday.auth.dto.login.response.LoginResponse;
import com.goodda.jejuday.auth.dto.register.request.EmailSenderRequest;
import com.goodda.jejuday.auth.dto.register.request.EmailValidationRequest;
import com.goodda.jejuday.auth.dto.register.request.FinalAppRegisterRequest;
import com.goodda.jejuday.auth.dto.register.request.TempAppRegisterRequest;
import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.security.JwtService;
import com.goodda.jejuday.auth.service.EmailService;
import com.goodda.jejuday.auth.service.EmailVerificationService;
import com.goodda.jejuday.auth.service.KakaoService;
import com.goodda.jejuday.auth.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ControllerUnitTest {

    @Mock
    private UserService userService;
    @Mock
    private EmailService emailService;
    @Mock
    private KakaoService kakaoService;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private RegisterController registerController;

    ControllerUnitTest() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("/email/send 이메일 전송")
    void sendVerificationEmail_success() {
        EmailValidationRequest req = EmailValidationRequest.builder().email("user@naver.com").build();
        ResponseEntity<ApiResponse<String>> res = registerController.sendVerificationEmail(req);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(emailService).sendRegistrationVerificationEmail("user@naver.com");
    }

    @Test
    @DisplayName("/email/verify 인증 확인")
    void verifyEmail_success() {
        EmailSenderRequest req = EmailSenderRequest.builder().email("test@naver.com").code("123456").build();
        when(emailVerificationService.verifyEmailCodeForRegistration("test@naver.com", "123456")).thenReturn(true);
        ResponseEntity<ApiResponse<String>> res = registerController.verifyEmail(req);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("/account 탈퇴")
    void deactivateUser_success() {
        String email = "user@example.com";
        AccountController accountController = new AccountController(userService);
        ResponseEntity<ApiResponse<String>> res = accountController.deleteUsers(email);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).deleteUsers(email);
    }

    @Test
    @DisplayName("/profile 이미지 업로드")
    void updateProfileImage_success() {
        ProfileController profileController = new ProfileController(userService);
        MultipartFile file = new MockMultipartFile("newProfileImage", "img.png", MediaType.IMAGE_PNG_VALUE, new byte[10]);
        when(userService.getAuthenticatedUserId()).thenReturn(1L);
        when(userService.getProfileImageUrl(1L)).thenReturn("https://~/old.png");
        when(userService.uploadProfileImage(file)).thenReturn("https://~/new.png");
        ResponseEntity<ApiResponse<String>> res = profileController.updateProfileImage(file);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).deleteFile("https://~/old.png");
        verify(userService).uploadProfileImage(file);
        verify(userService).updateUserProfileImage(1L, "https://~/new.png");
    }

    @Test
    @DisplayName("/profile 이미지 삭제")
    void deleteProfileImage_success() {
        ProfileController profileController = new ProfileController(userService);
        when(userService.getAuthenticatedUserId()).thenReturn(1L);
        when(userService.getProfileImageUrl(1L)).thenReturn("https://~/profile.jpg");
        ResponseEntity<ApiResponse<String>> res = profileController.deleteProfileImage();
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).deleteFile("https://~/profile.jpg");
        verify(userService).updateUserProfileImage(1L, null);
    }

    @Test
    @DisplayName("/profile 이미지 조회")
    void getProfileImage_success() {
        ProfileController profileController = new ProfileController(userService);
        when(userService.getAuthenticatedUserId()).thenReturn(1L);
        when(userService.getProfileImageUrl(1L)).thenReturn("url.jpg");
        ResponseEntity<ApiResponse<String>> res = profileController.getProfileImage();
        assertThat(res.getBody().getData()).isEqualTo("url.jpg");
    }
}
