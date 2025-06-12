package com.goodda.jejuday.Auth.service.impl;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.goodda.jejuday.Auth.dto.login.response.LoginResponse;
import com.goodda.jejuday.Auth.entity.Language;
import com.goodda.jejuday.Auth.entity.Platform;
import com.goodda.jejuday.Auth.entity.TemporaryUser;
import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Auth.entity.UserTheme;
import com.goodda.jejuday.Auth.repository.EmailVerificationRepository;
import com.goodda.jejuday.Auth.repository.TemporaryUserRepository;
import com.goodda.jejuday.Auth.repository.UserRepository;
import com.goodda.jejuday.Auth.service.UserService;
import com.goodda.jejuday.Auth.util.exception.BadRequestException;
import com.goodda.jejuday.Auth.util.exception.CustomS3Exception;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryUserRepository temporaryUserRepository;
    private final EmailVerificationRepository emailVerificationRepository;

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email: " + email));
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
                .language(user.getLanguage())
                .platform(user.getPlatform())
                .themes(user.getUserThemes().stream()
                        .map(UserTheme::getName)
                        .toList())
                .build();
    }

    @Override
    public String uploadProfileImage(MultipartFile profileImage) {
        String originalFilename = profileImage.getOriginalFilename();
        String savedFilename = UUID.randomUUID().toString() + "_" + originalFilename;
        String key = "profile-images/" + savedFilename;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(profileImage.getSize());
        metadata.setContentType(profileImage.getContentType());

        try {
            amazonS3.putObject(bucketName, key, profileImage.getInputStream(), metadata);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload profile image to S3", e);
        }

        return amazonS3.getUrl(bucketName, key).toString();
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
        String httpsPrefix = "https://~/";
        String s3Prefix = "s3://버킷명/";

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
    public void saveTemporaryUser(String email, String password, Platform platform, Language language) {
        language = Optional.ofNullable(language).orElse(Language.KOREAN);

        TemporaryUser.TemporaryUserBuilder builder = TemporaryUser.builder()
                .email(email)
                .platform(platform)
                .language(language);

        if (password != null && !password.isBlank()) {
            builder.password(passwordEncoder.encode(password));
        }

        temporaryUserRepository.save(builder.build());
    }

    @Override
    @Transactional
    public void completeFinalRegistration(String email, String nickname, String profile, Set<UserTheme> userThemes) {
        TemporaryUser tempUser = temporaryUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Temporary user not found."));

        if (userRepository.existsByNickname(nickname)) {
            throw new BadRequestException("이미 사용 중인 닉네임 입니다!");
        }

        emailVerificationRepository.deleteByTemporaryUser_Email(email);
        completeRegistration(email, nickname, profile, userThemes);
        temporaryUserRepository.delete(tempUser);
    }

    @Override
    @Transactional
    public void completeRegistration(String email, String nickname, String profile, Set<UserTheme> userThemes) {
        TemporaryUser tempUser = temporaryUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Temporary user not found."));

        String password = tempUser.getPlatform() == Platform.KAKAO ? null : tempUser.getPassword();

        User user = User.builder()
                .email(tempUser.getEmail())
                .password(password)
                .nickname(nickname)
                .platform(tempUser.getPlatform())
                .profile(profile)
                .createdAt(LocalDateTime.now())
                .language(tempUser.getLanguage())
                .userThemes(userThemes)
                .active(true)
                .build();

        userRepository.save(user);

        if (tempUser.getPlatform() != Platform.KAKAO) {
            emailVerificationRepository.deleteByTemporaryUser_TemporaryUserId(tempUser.getTemporaryUserId());
        }

        temporaryUserRepository.delete(tempUser);
    }

    @Override
    @Transactional
    public void deactivate(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void deleteUsers() {
        List<User> usersToDelete = userRepository.findByActiveFalseAndDeletionScheduledAtBefore(LocalDateTime.now());
        usersToDelete.forEach(user -> {
            String profile = user.getProfile();
            if (profile != null && !profile.isBlank()) {
                deleteFile(profile);
            }
        });
        userRepository.deleteAll(usersToDelete);
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
//        User user = userRepository.findById(userId)
//                .orElseThrow(() -> new BadRequestException("User not found"));
//        user.setFcmToken(fcmToken);
    }

    @Override
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found with id: " + userId));
    }
}
