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

//    @Test
//    @DisplayName("/app 회원가입")
//    void registerAppUser_success() {
//        TempAppRegisterRequest req = TempAppRegisterRequest.builder()
//                .email("a@a.com")
//                .build();
//
//        ResponseEntity<ApiResponse<String>> res = registerController.registerAppUser(req);
//
//        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
//        assertThat(res.getBody().getData()).contains("임시 사용자");
//
//        verify(userService).saveTemporaryUser(
//                anyString(),
//                anyString(),
//                anyString(),
//                eq(Platform.APP),
//                eq(Language.KOREAN)
//            );
//    }

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
        when(emailVerificationService.verifyTemporaryUserCode("test@naver.com", "123456")).thenReturn(true);

        ResponseEntity<ApiResponse<String>> res = registerController.verifyEmail(req);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

//    @Test
//    @DisplayName("/final 회원가입 완료")
//    void completeRegistration_success() throws IOException {
//        FinalAppRegisterRequest req = FinalAppRegisterRequest.builder()
//                .email("hi@hi.com")
//                .password("asd@asd123")
//                .nickname("닉네임")
//                .themes(List.of("산책", "휴식"))
//                .birthYear("1950")
//                .build();
//
//        MultipartFile file = mock(MultipartFile.class);
//        when(file.isEmpty()).thenReturn(false);
//        when(file.getOriginalFilename()).thenReturn("pic.jpg");
//        when(file.getSize()).thenReturn(10L);
//        when(file.getContentType()).thenReturn("image/jpeg");
//        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[10]));
//
//        when(userService.uploadProfileImage(file)).thenReturn("https://profile/pic.jpg");
//
//        User mockUser = User.builder().email("hi@hi.com").build();
//        when(userService.getUserByEmail("hi@hi.com")).thenReturn(mockUser);
//        doNothing().when(kakaoService).authenticateUser(any(User.class));
//
//        ResponseEntity<ApiResponse<LoginResponse>> res = registerController.completeRegistration(req,file, Gender.MALE,
//                response);
//
//        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
//        verify(userService).completeFinalRegistration(eq("hi@hi.com"), eq("asd@asd123"),eq("닉네임"), eq("https://profile/pic.jpg"), any(),
//                any(), eq("1950"), "aaaa");
//        verify(userService).setLoginCookie(response, "hi@hi.com");
//        verify(kakaoService).authenticateUser(mockUser);
//    }

//    @Test
//    @DisplayName("/auth/login 로그인")
//    void login_success() {
//        AuthController authController = new AuthController(userService, emailService, emailVerificationService,
//                jwtService, userRepository);
//        LoginRequest req = LoginRequest.builder().email("a@a.com").password("pass1234").build();
//
//        User user = new User(userId);
//        user.setEmail("a@a.com");
//
//        when(userService.getUserByEmailOrNull("a@a.com")).thenReturn(user);
//        when(userService.matchesPassword("pass1234", null)).thenReturn(true);
//
//        when(userService.loginResponse(user)).thenReturn(new LoginResponse());
//
//        ResponseEntity<LoginResponse> res = authController.login(req, response);
//
//        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
//        verify(userService).setLoginCookie(response, "a@a.com");
//    }

    @Test
    @DisplayName("/account 탈퇴")
    void deactivateUser_success() {
        // given
        String email = "user@example.com";

        AccountController accountController = new AccountController(userService);

        // when
        ResponseEntity<ApiResponse<String>> res = accountController.deleteUsers(email);

        // then
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).deleteUsers(email);
    }


    @Test
    @DisplayName("/profile 이미지 업로드")
    void updateProfileImage_success() {
        ProfileController profileController = new ProfileController(userService);
        MultipartFile file = new MockMultipartFile("newProfileImage", "img.png", MediaType.IMAGE_PNG_VALUE,
                new byte[10]);

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