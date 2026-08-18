package com.goodda.jejuday.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.TemporaryUser;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.EmailVerificationRepository;
import com.goodda.jejuday.auth.repository.TemporaryUserRepository;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.repository.UserThemeRepository;
import com.goodda.jejuday.auth.service.TemporaryUserService;
import com.goodda.jejuday.auth.service.UserHardDeleteService;
import com.goodda.jejuday.common.exception.BadRequestException;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

public class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TemporaryUserRepository temporaryUserRepository;
    @Mock
    private UserThemeRepository userThemeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AmazonS3 amazonS3;
    @Mock
    private EmailVerificationRepository emailVerificationRepository;
    @Mock
    private MultipartFile mockFile;
    @Mock
    private Authentication authentication;
    @Mock
    private UserDetails userDetails;
    @Mock
    private TemporaryUserService temporaryUserService;
    @Mock
    private UserHardDeleteService userHardDeleteService;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(userService, "bucketName", "dummy-bucket"); // 🔧 중요
    }

    @Test
    @DisplayName("matchesPassword: 일치")
    void matchesPassword_success() {
        when(passwordEncoder.matches("raw", "encoded")).thenReturn(true);
        assertThat(userService.matchesPassword("raw", "encoded")).isTrue();
    }

    @Test
    @DisplayName("matchesPassword: 불일치")
    void matchesPassword_fail() {
        when(passwordEncoder.matches("raw", "encoded")).thenReturn(false);
        assertThat(userService.matchesPassword("raw", "encoded")).isFalse();
    }

    @Test
    @DisplayName("resetPassword: 성공")
    void resetPassword_success() {
        User user = new User();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass")).thenReturn("encoded");
        userService.resetPassword("test@example.com", "newpass");
        assertThat(user.getPassword()).isEqualTo("encoded");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("resetPassword: 유저 없음")
    void resetPassword_userNotFound() {
        when(userRepository.findByEmail("nope@example.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> userService.resetPassword("nope@example.com", "1234"));
    }

    @Test
    @DisplayName("getUserByEmail: 성공")
    void getUserByEmail_success() {
        User user = new User();
        when(userRepository.findByEmail("abc@test.com")).thenReturn(Optional.of(user));
        assertThat(userService.getUserByEmail("abc@test.com")).isEqualTo(user);
    }

    @Test
    @DisplayName("getUserByEmail: 실패")
    void getUserByEmail_notFound() {
        when(userRepository.findByEmail("notfound@test.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> userService.getUserByEmail("notfound@test.com"));
    }

    @Test
    @DisplayName("getUserByEmailOrNull: 존재")
    void getUserByEmailOrNull() {
        User user = new User();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        assertThat(userService.getUserByEmailOrNull("a@b.com")).isEqualTo(user);
    }

//    @Test
//    @DisplayName("saveTemporaryUser: 저장")
//    void saveTemporaryUser_success() {
//        userService.saveTemporaryUser("test@test.com", "password", Platform.APP, Language.KOREAN);
//        verify(temporaryUserService).save(
//                eq(Language.KOREAN),
//                eq(Platform.APP),
//                eq("홍길동"),
//                eq("test@test.com"),
//                eq("password")
//        );
//    }

    @Test
    @DisplayName("uploadProfileImage: S3 업로드")
    void uploadProfileImage_success() throws Exception {
        when(mockFile.getOriginalFilename()).thenReturn("photo.jpg");
        when(mockFile.getSize()).thenReturn(100L);
        when(mockFile.getContentType()).thenReturn("image/jpeg");
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));

        when(amazonS3.getUrl(anyString(), anyString()))
                .thenReturn(new URL("https://dummy-bucket.s3.amazonaws.com/profile-images/photo.jpg"));

        String result = userService.uploadProfileImage(mockFile);
        assertThat(result).contains("profile-images/");
    }

    @Test
    @DisplayName("deleteFile: 정상 삭제")
    void deleteFile_success() {
        userService.deleteFile("https://~/profile-images/test.jpg");
        verify(amazonS3).deleteObject(any(DeleteObjectRequest.class));
    }

//    @Test
//    @DisplayName("completeFinalRegistration: 최종 회원 등록")
//    void completeFinalRegistration_success() {
//        TemporaryUser temp = TemporaryUser.builder()
//                .temporaryUserId(1L)
////                .name("홍길동")
//                .email("email@test.com")
//                .platform(Platform.APP)
//                .language(Language.KOREAN)
//                .build();
//
//        when(temporaryUserRepository.findByEmail("email@test.com")).thenReturn(Optional.of(temp));
//        when(userRepository.existsByNickname("nickname")).thenReturn(false);
//
//        userService.completeFinalRegistration("email@test.com", "profileUrl", Set.of("산책"), Gender.MALE, "1950", "");
//
//        verify(emailVerificationRepository).deleteByTemporaryUser_TemporaryUserId(1L);
//        verify(temporaryUserRepository).delete(temp);
//    }

    @Test
    @DisplayName("getAuthenticatedUserId: 인증된 유저 ID 반환")
    void getAuthenticatedUserId_success() {
        User user = new User();
        user.setId(42L);

        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Long result = userService.getAuthenticatedUserId();
        assertThat(result).isEqualTo(42L);
    }

    @Test
    @DisplayName("getAuthenticatedUserId: 인증 실패")
    void getAuthenticatedUserId_unauthenticated() {
        SecurityContextHolder.clearContext();
        assertThrows(BadRequestException.class, () -> userService.getAuthenticatedUserId());
    }

    @Test
    @DisplayName("getUserById: 유저 조회 성공")
    void getUserById_success() {
        User user = new User();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        assertThat(userService.getUserById(1L)).isEqualTo(user);
    }

    @Test
    @DisplayName("getUserById: 유저 없음")
    void getUserById_fail() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> userService.getUserById(999L));
    }

    @Test
    @DisplayName("updateUserLanguage: 유저 언어 변경")
    void updateUserLanguage_success() {
        User user = new User();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateUserLanguage(1L, Language.ENGLISH);

        assertThat(user.getLanguage()).isEqualTo(Language.ENGLISH);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("deleteUsers: 이메일/비밀번호 기반 유저 삭제 성공")
    void deleteUser_success() {
        // given
        String email = "abc@def.com";
        String rawPassword = "test1234";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        User user = User.builder()
                .id(1L)
                .email(email)
                .password(encodedPassword)
                .profile("https://s3-url.com/profile.jpg")
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        // when
        userService.deleteUsers(email);

        // then
        verify(userHardDeleteService).deleteDependencies(1L);
        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("existsByEmail: 이메일 존재 여부 확인")
    void existsByEmail_success() {
        when(userRepository.existsByEmail("abc@def.com")).thenReturn(true);
        assertThat(userService.existsByEmail("abc@def.com")).isTrue();
    }

    @Test
    @DisplayName("existsByEmail: 이메일 존재 여부 확인")
    void existsByEmail_Fail() {
        when(userRepository.existsByEmail("abc@def.com")).thenReturn(true);
        assertThat(userService.existsByEmail("abdc@def.com")).isFalse();
    }

    @Test
    @DisplayName("existsByEmail: 이메일이 존재하지 않으면 false 반환")
    void existsByEmail_notExists() {
        // given
        String email = "abc@def.com";
        when(userRepository.existsByEmail(email)).thenReturn(false);

        // when & then
        assertThat(userService.existsByEmail(email)).isFalse();
    }



}
