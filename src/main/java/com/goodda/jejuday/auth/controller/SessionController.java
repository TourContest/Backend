package com.goodda.jejuday.auth.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.dto.login.response.LoginStatusResponse;
import com.goodda.jejuday.auth.dto.register.response.ProfileResponse;
import com.goodda.jejuday.auth.dto.register.response.UserSummaryResponse;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.entity.UserTheme;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Session", description = "로그인 상태 확인 및 내 프로필 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/users/session")
public class SessionController {

    private final UserService userService;
    private final UserRepository userRepository;

    @Operation(summary = "로그인 상태 확인", description = "JWT 쿠키 기반 인증 여부와 간단한 유저 정보를 반환합니다.")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<LoginStatusResponse>> status() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            LoginStatusResponse unauth = LoginStatusResponse.builder()
                    .authenticated(false)
                    .provider(null)
                    .userId(null)
                    .email(null)
                    .nickname(null)
                    .build();
            return ResponseEntity.ok(ApiResponse.onSuccess(unauth));
        }

        Long userId = userService.getAuthenticatedUserId();
        User user = userService.getUserById(userId);

        LoginStatusResponse ok = LoginStatusResponse.builder()
                .authenticated(true)
                .provider(user.isKakaoLogin() ? "KAKAO" : "APP")
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build();

        return ResponseEntity.ok(ApiResponse.onSuccess(ok));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> listAll() {
        List<UserSummaryResponse> list = userRepository.findAllWithThemes().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.onSuccess(list));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> me() {
        Long userId = userService.getAuthenticatedUserId();
        User user = userRepository.findByIdWithThemes(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Set<String> themes = user.getUserThemes() == null ? Set.of()
                : user.getUserThemes().stream().map(UserTheme::getName).collect(Collectors.toSet());

        ProfileResponse body = ProfileResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(null)
                .nickname(user.getNickname())
                .profile(user.getProfile())
                .language(user.getLanguage()) // 하드코딩 제거
                .platform(user.getPlatform())
                .gender(user.getGender())
                .birthYear(user.getBirthYear())
                .themes(themes)
                .notificationEnabled(user.isNotificationEnabled())
                .createdAt(user.getCreatedAt())
                .hallabong(user.getHallabong())
                .totalSteps(user.getTotalSteps())
                .moodGrade(user.getMoodGrade())
                .build();

        return ResponseEntity.ok(ApiResponse.onSuccess(body));
    }

    private UserSummaryResponse toSummary(User u) {
        Set<String> themes = u.getUserThemes() == null ? Set.of()
                : u.getUserThemes().stream().map(UserTheme::getName).collect(Collectors.toSet());

        return UserSummaryResponse.builder()
                .userId(u.getId())
                .email(u.getEmail())
                .name(null)
                .nickname(u.getNickname())
                .platform(u.getPlatform())
                .kakaoLogin(u.isKakaoLogin())
                .gender(u.getGender())
                .birthYear(u.getBirthYear())
                .language(u.getLanguage())
                .createdAt(u.getCreatedAt())
                .hallabong(u.getHallabong())
                .totalSteps(u.getTotalSteps())
                .themes(themes)
                .build();
    }
}
