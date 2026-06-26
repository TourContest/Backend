package com.goodda.jejuday.auth.service.impl;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.goodda.jejuday.auth.dto.login.response.LoginResponse;
import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.TemporaryUser;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.entity.UserTheme;
import com.goodda.jejuday.auth.repository.EmailVerificationRepository;
import com.goodda.jejuday.auth.repository.TemporaryUserRepository;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.repository.UserThemeRepository;
import com.goodda.jejuday.auth.security.JwtService;
import com.goodda.jejuday.auth.service.EmailVerificationService;
import com.goodda.jejuday.auth.service.ReferralService;
import com.goodda.jejuday.auth.service.TemporaryUserService;
import com.goodda.jejuday.auth.service.UserService;
import com.goodda.jejuday.common.exception.BadRequestException;
import com.goodda.jejuday.common.exception.CustomS3Exception;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    private final AmazonS3 amazonS3;
    private final JwtService jwtService;
    private final TemporaryUserService temporaryUserService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryUserRepository temporaryUserRepository;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationRepository emailVerificationRepository;
    private final UserThemeRepository userThemeRepository;
    private final ReferralService referralService;

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다." + email));
    }

    @Override
    public void setLoginCookie(HttpServletResponse response, String email) {
        jwtService.addAccessTokenCookie(response, email); // 내부적으로 role은 고정되었거나 기본값일 수 있음
    }

    @Override
    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    @Transactional
    public void resetPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        String encoded = passwordEncoder.encode(newPassword);
        user.setPassword(encoded);
        userRepository.save(user);
    }

    @Override
    public User getUserByEmailOrNull(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    @Override
    public LoginResponse loginResponse(User user) {
        return LoginResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profile(user.getProfile())
                .birthYear(user.getBirthYear())
                .language(user.getLanguage())
                .platform(user.getPlatform())
                .themes(user.getUserThemes().stream()
                        .map(UserTheme::getName)
                        .toList())
                .build();
    }

    @Override
    public String uploadProfileImage(MultipartFile profileImage) {
        validateProfileImage(profileImage);

        String key = generateImageKey(profileImage.getOriginalFilename());
        ObjectMetadata metadata = createMetadata(profileImage);

        uploadToS3(profileImage, key, metadata);

        return getImageUrl(key);
    }

    private void validateProfileImage(MultipartFile profileImage) {
        if (profileImage == null || profileImage.isEmpty()) {
            throw new IllegalArgumentException("프로필 이미지가 비어있습니다.");
        }

        String originalFilename = profileImage.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new IllegalArgumentException("파일명이 유효하지 않습니다.");
        }

        String contentType = profileImage.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }
    }

    private String generateImageKey(String originalFilename) {
        String savedFilename = UUID.randomUUID().toString() + "_" + originalFilename;
        return "profile-images/" + savedFilename;
    }

    private ObjectMetadata createMetadata(MultipartFile profileImage) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(profileImage.getSize());
        metadata.setContentType(profileImage.getContentType());
        return metadata;
    }

    private void uploadToS3(MultipartFile profileImage, String key, ObjectMetadata metadata) {
        try {
            amazonS3.putObject(bucketName, key, profileImage.getInputStream(), metadata);
        } catch (IOException e) {
            throw new RuntimeException("S3 프로필 이미지 업로드에 실패했습니다.", e);
        }
    }

    private String getImageUrl(String key) {
        URL url = amazonS3.getUrl(bucketName, key);
        if (url == null) {
            throw new RuntimeException("S3에서 이미지 URL을 가져올 수 없습니다.");
        }
        return url.toString();
    }

    @Override
    public void deleteFile(String fileUrl) {
        String key = extractFileName(fileUrl);
        System.out.println("삭제 시도할 S3 key: " + key);

        try {
            amazonS3.deleteObject(new DeleteObjectRequest(bucketName, key));
            System.out.println("삭제 완료");
        } catch (AmazonServiceException e) {
            System.out.println("AmazonServiceException 발생: " + e.getErrorMessage());
            throw new CustomS3Exception("S3 삭제 실패: " + e.getMessage(), e);
        } catch (SdkClientException e) {
            System.out.println("SdkClientException 발생: " + e.getMessage());
            throw new CustomS3Exception("S3 클라이언트 오류: " + e.getMessage(), e);
        }
    }


    private String extractFileName(String fileUrl) {
        String httpsPrefix = "https://jejudaybucket123.s3.amazonaws.com/";
        String s3Prefix = "s3://jejudaybucket123/";

        if (fileUrl.startsWith(httpsPrefix)) {
            return fileUrl.replace(httpsPrefix, "");
        } else if (fileUrl.startsWith(s3Prefix)) {
            return fileUrl.replace(s3Prefix, "");
        }
        return fileUrl;
    }

    @Override
    public String getProfileImageUrl(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
        return user.getProfile();
    }

    @Override
    public void updateUserProfileImage(Long userId, String newProfileUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
        user.setProfile(newProfileUrl);
        userRepository.save(user);
    }

    @Override
    public void saveTemporaryUser(String name, String email, String passwordOrProfile, Platform platform,
                                  Language language) {
        temporaryUserService.save(language, platform, null, email, passwordOrProfile);
    }

    @Override
    @Transactional
    public void completeFinalRegistration(String email, String nickname, String profile, Set<String> themeNames,
                                          Gender gender, String birthYear, String referrerNickname) {
        // 임시 사용자 존재 확인 (이메일 인증 및 비밀번호 설정 완료된 사용자)
        TemporaryUser tempUser = temporaryUserRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("임시 사용자를 찾을 수 없습니다. 이메일 인증 및 비밀번호 설정을 먼저 완료해주세요."));

        if (userRepository.existsByNickname(nickname)) {
            throw new BadRequestException("이미 사용 중인 닉네임 입니다!");
        }

        Set<UserTheme> userThemes = themeNames != null
                ? themeNames.stream()
                .map(name -> userThemeRepository.findByName(name)
                        .orElseGet(() -> userThemeRepository.save(UserTheme.builder().name(name).build())))
                .collect(Collectors.toSet())
                : Set.of();

        // 사용자 등록 완료
        User newUser = completeRegistration(email, nickname, profile, userThemes, gender, birthYear);

        // 추천인 처리 (사용자 등록 후 진행)
        if (referrerNickname != null && !referrerNickname.trim().isEmpty()) {
            try {
                referralService.processSignupBonus(newUser.getId(), referrerNickname);
            } catch (Exception e) {
                log.warn("추천인 처리 중 오류 발생: {}", e.getMessage());
                // 추천인 처리 실패해도 회원가입은 성공으로 처리
            }
        }
    }


    @Override
    @Transactional
    public User completeRegistration(String email, String nickname, String profile, Set<UserTheme> userThemes,
                                     Gender gender, String birthYear) {
        TemporaryUser tempUser = temporaryUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("임시 사용자를 찾을 수 없습니다."));

        User user = User.builder()
//                .name(tempUser.getName())
                .email(tempUser.getEmail())
                .password(tempUser.getPassword())
                .nickname(nickname)
                .platform(Platform.APP)
                .language(tempUser.getLanguage())
                .gender(gender)
                .birthYear(birthYear)
                .profile(profile != null ? profile : tempUser.getProfile())
                .userThemes(userThemes)
                .isKakaoLogin(false)
                .hallabong(0)
                .build();

        User savedUser = userRepository.save(user);

        if (tempUser.getPlatform() != Platform.KAKAO) {
            emailVerificationRepository.deleteByTemporaryUser_TemporaryUserId(tempUser.getTemporaryUserId());
        }

        temporaryUserRepository.delete(tempUser);
        return savedUser;
    }

    @Override
    @Transactional
    public void deleteUsers(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다"));

        String profile = user.getProfile();
        if (profile != null && !profile.isBlank()) {
            deleteFile(profile); // S3 프로필 이미지 삭제
        }

        userRepository.delete(user);
    }

    @Override
    @Transactional
    public void updateUserLanguage(Long userId, Language language) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        user.setLanguage(language);
        userRepository.save(user);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    @Override
    public Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            String email = ((UserDetails) authentication.getPrincipal()).getUsername();
            return getUserByEmail(email).getId();
        }
        throw new BadRequestException("User is not authenticated.");
    }

    @Override
    @Transactional
    public void updateFcmToken(Long userId, String fcmToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("유저가 존재하지 않습니다."));
        user.setFcmToken(fcmToken);
        if (!user.isNotificationEnabled()) {
            user.setNotificationEnabled(true);
        }
        userRepository.save(user);
    }

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("사용자를 찾을 수 없습니다.: " + userId));
    }

    @Override
    @Transactional
    public void updateNotificationSetting(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        user.setNotificationEnabled(enabled);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void logoutUser(Long userId, HttpServletResponse response) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        user.setFcmToken(null);
        userRepository.save(user);
        jwtService.clearAccessTokenCookie(response);
    }

    @Override
    @Transactional
    public void updateNickname(Long userId, String newNickname) {
        if (userRepository.existsByNickname(newNickname)) {
            throw new BadRequestException("이미 사용 중인 닉네임입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        user.setNickname(newNickname);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void updateUserThemes(Long userId, Set<String> themeNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        Set<UserTheme> userThemes = themeNames.stream()
                .map(name -> userThemeRepository.findByName(name)
                        .orElseGet(() -> userThemeRepository.save(UserTheme.builder().name(name).build())))
                .collect(Collectors.toSet());

        user.setUserThemes(userThemes);
        userRepository.save(user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByEmail(username);
    }
}
