package com.goodda.jejuday.Auth.controller;

import com.goodda.jejuday.Auth.dto.ApiResponse;
import com.goodda.jejuday.Auth.dto.login.request.LoginRequest;
import com.goodda.jejuday.Auth.dto.login.response.LoginResponse;
import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Auth.repository.UserRepository;
import com.goodda.jejuday.Auth.security.JwtService;
import com.goodda.jejuday.Auth.service.EmailService;
import com.goodda.jejuday.Auth.service.EmailVerificationService;
import com.goodda.jejuday.Auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "로그인 및 비밀번호 찾기 API")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/v1/users/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Operation(summary = "일반 로그인", description = "이메일과 비밀번호로 로그인합니다.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        User user = userService.getUserByEmailOrNull(request.getEmail());
        if (user == null || !userService.matchesPassword(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        userService.setLoginCookie(response, user.getEmail());
        return ResponseEntity.ok(userService.loginResponse(user));
    }

    @Operation(summary = "비밀번호 재설정 이메일 전송", description = "비밀번호 재설정을 위한 인증 이메일을 발송합니다.")
    @PostMapping("/find/email/send")
    public ResponseEntity<ApiResponse<String>> sendResetEmail(@RequestParam String email) {
        emailService.sendPasswordResetVerificationEmail(email);
        return new ResponseEntity<>(ApiResponse.onSuccess("비밀번호 재설정 이메일 전송 완료"), HttpStatus.OK);
    }

    @Operation(summary = "재설정 인증 코드 확인", description = "전송된 인증 코드를 검증합니다.")
    @PostMapping("/find/email/verify")
    public ResponseEntity<ApiResponse<String>> verifyResetCode(@RequestParam String email, @RequestParam String code) {
        boolean verified = emailVerificationService.verifyUserCode(email, code);
        if (!verified) {
            throw new IllegalArgumentException("인증 코드가 유효하지 않습니다.");
        }
        return new ResponseEntity<>(ApiResponse.onSuccess("인증 완료"), HttpStatus.OK);
    }

    @Operation(summary = "비밀번호 재설정", description = "새 비밀번호로 비밀번호를 변경합니다.")
    @PostMapping("/find/password/reset")
    public ResponseEntity<ApiResponse<String>> resetPassword(@RequestParam String email,
                                                             @RequestParam String newPassword) {
        userService.resetPassword(email, newPassword);
        return new ResponseEntity<>(ApiResponse.onSuccess("비밀번호가 성공적으로 변경되었습니다."), HttpStatus.OK);
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "JWT 쿠키를 삭제하고 FCM 토큰을 제거하여 로그아웃 처리합니다.")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletResponse response) {
        jwtService.clearAccessTokenCookie(response);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
            String email = userDetails.getUsername();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            userService.logoutUser(user.getId());
        }

        return ResponseEntity.ok(ApiResponse.onSuccess("로그아웃 성공"));
    }

}