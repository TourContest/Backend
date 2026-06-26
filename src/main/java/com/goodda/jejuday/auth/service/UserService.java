package com.goodda.jejuday.auth.service;

import com.goodda.jejuday.auth.dto.login.response.LoginResponse;
import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.entity.UserTheme;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;
import java.util.Optional;

public interface UserService {
    User getUserByEmail(String email);

    void setLoginCookie(HttpServletResponse response, String email);

    boolean matchesPassword(String rawPassword, String encodedPassword);

    void resetPassword(String email, String newPassword);

    User getUserByEmailOrNull(String email);

    LoginResponse loginResponse(User user);

    String uploadProfileImage(MultipartFile profileImage);

    void deleteFile(String fileUrl);

    String getProfileImageUrl(Long userId);

    void updateUserProfileImage(Long userId, String newProfileUrl);

    void saveTemporaryUser(String name, String email, String password, Platform platform, Language language);

    void completeFinalRegistration(String email, String nickname, String profile, Set<String> themeNames, Gender gender,
                                   String birthYear, String referrerNickname);

    User completeRegistration(String email, String nickname, String profile, Set<UserTheme> userThemes, Gender gender,
                              String birthYear);

    void deleteUsers(String email);

    void updateUserLanguage(Long userId, Language language);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    Long getAuthenticatedUserId();

    void updateFcmToken(Long userId, String fcmToken);

    User getUserById(Long userId);

    void updateNotificationSetting(Long userId, boolean enabled);

    void logoutUser(Long userId, HttpServletResponse response);

    void updateNickname(Long userId, String newNickname);

    void updateUserThemes(Long userId, Set<String> themeNames);

    Optional<User> findByUsername(String username);
}
