package com.goodda.jejuday.Auth.service;

import com.goodda.jejuday.Auth.dto.login.response.LoginResponse;
import com.goodda.jejuday.Auth.entity.Language;
import com.goodda.jejuday.Auth.entity.Platform;
import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Auth.entity.UserTheme;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    User getUserByEmail(String email);

    User getUserByEmailOrNull(String email);

    LoginResponse loginResponse(User user);

    String uploadProfileImage(MultipartFile profileImage);

    void deleteFile(String fileUrl);

    String getProfileImageUrl(Long userId);

    void updateUserProfileImage(Long userId, String newProfileUrl);

    void saveTemporaryUser(String email, String password, Platform platform, Language language);

    void completeFinalRegistration(String email, String nickname, String profile, Set<UserTheme> userThemes);

    void completeRegistration(String email, String nickname, String profile, Set<UserTheme> userThemes);

    void deactivate(Long userId);

    void deleteUsers();

    void updateUserLanguage(Long userId, Language language);

    boolean existsByEmail(String email);

    Long getAuthenticatedUserId();

    void updateFcmToken(Long userId, String fcmToken);

    User getUserById(Long userId);
}
