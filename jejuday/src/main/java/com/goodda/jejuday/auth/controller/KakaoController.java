package com.goodda.jejuday.auth.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.dto.KakaoDTO;
import com.goodda.jejuday.auth.dto.login.response.LoginResponse;
import com.goodda.jejuday.auth.dto.register.request.KakaoFinalRegisterRequest;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.security.JwtService;
import com.goodda.jejuday.auth.service.KakaoService;
import com.goodda.jejuday.auth.service.UserService;
import com.goodda.jejuday.common.exception.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
                userService.logoutUser(user.getId(), response); // FCM 토큰 제거
            }
        }

        return ResponseEntity.ok(ApiResponse.onSuccess("카카오 로그아웃 성공"));
    }

    @GetMapping("/callback")
    public String handleKakaoCallback(
            @RequestParam("code") String code) {
        return "redirect:/kakao-join?code=" + code;
    }

    @PostMapping("/final-register")
    @Operation(summary = "카카오 최종 회원가입 및 로그인", description = "카카오 인증 후 회원가입을 완료하고 JWT 발급")
    public ResponseEntity<ApiResponse<LoginResponse>> kakaoFinalRegister(
            @RequestBody KakaoFinalRegisterRequest request,
            HttpServletResponse response) {

        KakaoDTO kakaoDTO = kakaoService.getKakaoUserInfo(request.getCode());

        User existing = userService.getUserByEmailOrNull(kakaoDTO.getAccountEmail());
        if (existing != null) {
            throw new BadRequestException("이미 가입된 카카오 계정입니다. 로그인 해주세요.");
        }

        kakaoService.registerKakaoUser(
                kakaoDTO.getAccountEmail(),
                request.getNickname(),
                kakaoDTO.getProfileImageUrl(),
                Set.copyOf(request.getThemes()),
                request.getGender(),
                Language.KOREAN,
                request.getBirthYear(),
                request.getReferrerNickname()
        );

        User newUser = userService.getUserByEmail(kakaoDTO.getAccountEmail());
        userService.setLoginCookie(response, newUser.getEmail());

        kakaoService.authenticateUser(newUser);

        return ResponseEntity.ok(ApiResponse.onSuccess(userService.loginResponse(newUser)));
    }

}

