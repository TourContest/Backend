package com.goodda.jejuday.Auth.controller;

import com.goodda.jejuday.Auth.dto.ApiResponse;
import com.goodda.jejuday.Auth.dto.KakaoDTO;
import com.goodda.jejuday.Auth.dto.login.response.LoginResponse;
import com.goodda.jejuday.Auth.dto.register.request.FinalAppRegisterRequest;
import com.goodda.jejuday.Auth.entity.Language;
import com.goodda.jejuday.Auth.entity.Platform;
import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Auth.security.JwtService;
import com.goodda.jejuday.Auth.service.KakaoService;
import com.goodda.jejuday.Auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/users/kakao")
@Slf4j
public class KakaoController {

    private final KakaoService kakaoService;
    private final UserService userService;
    private final JwtService jwtService;

    @GetMapping("/login-url")
    public ResponseEntity<ApiResponse<String>> getKakaoLoginUrl() {
        String loginUrl = kakaoService.getKakaoLoginUrl();
        return ResponseEntity.ok(ApiResponse.onSuccess(loginUrl));
    }

    @Operation(summary = "카카오 임시 사용자 등록", description = "카카오 회원가입 시 임시 사용자로 저장합니다.")
    @GetMapping("/preload")
    public ResponseEntity<ApiResponse<FinalAppRegisterRequest>> preloadKakaoUser(
            @RequestParam String code,
            @CookieValue(value = "language", required = false) Language language) {

        KakaoDTO kakaoDTO = kakaoService.getKakaoUserInfo(code);

        userService.saveTemporaryUser(
                kakaoDTO.getNickname(),
                kakaoDTO.getAccountEmail(),
                kakaoDTO.getProfileImageUrl(),
                Platform.KAKAO,
                language
        );

        FinalAppRegisterRequest request = kakaoService.convertToFinalRequest(kakaoDTO);
        return ResponseEntity.ok(ApiResponse.onSuccess(request));
    }

    @PostMapping("/login")
    @Operation(summary = "카카오 로그인", description = "카카오 계정으로 로그인합니다.")
    public ResponseEntity<LoginResponse> kakaoLogin(@RequestParam("email") String email, HttpServletResponse response) {
        User user = userService.getUserByEmailOrNull(email);

        if (user == null) {
            throw new IllegalArgumentException("등록되지 않은 이메일입니다. 회원가입을 먼저 진행해주세요.");
        }

        if (!user.isKakaoLogin()) {
            throw new IllegalArgumentException("일반 로그인 계정입니다. 이메일 로그인 API를 사용해주세요.");
        }

        userService.setLoginCookie(response, user.getEmail());
        return ResponseEntity.ok(userService.loginResponse(user));
    }

    @PostMapping("/logout")
    @Operation(summary = "카카오 로그아웃", description = "JWT 쿠키를 삭제하고 FCM 토큰을 제거합니다.")
    public ResponseEntity<ApiResponse<String>> kakaoLogout(HttpServletResponse response) {
        jwtService.clearAccessTokenCookie(response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            String email = userDetails.getUsername();
            User user = userService.getUserByEmailOrNull(email);

            if (user != null && user.isKakaoLogin()) {
                userService.logoutUser(user.getId()); // FCM 토큰 제거
            }
        }

        return ResponseEntity.ok(ApiResponse.onSuccess("카카오 로그아웃 성공"));
    }

    @GetMapping("/callback")
    public String handleKakaoCallback(
            @RequestParam("code") String code) {
        return "redirect:/kakao-join?code=" + code;
    }
}

