package com.goodda.jejuday.Auth.controller;

import com.goodda.jejuday.Auth.dto.ApiResponse;
import com.goodda.jejuday.Auth.entity.Language;
import com.goodda.jejuday.Auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "사용자 탈퇴 API")
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/v1/users/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserService userService;

    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 사용자의 계정을 비활성화합니다.")
    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<String>> deleteUsers(@RequestParam String email) {
        userService.deleteUsers(email);
        return new ResponseEntity<>(ApiResponse.onSuccess("회원 탈퇴 처리가 완료되었습니다."), HttpStatus.OK);
    }

    // 언어 변경
    @PutMapping("language/change")
    @Operation(summary = "언어 변경", description = "언어 변경")
    public ResponseEntity<ApiResponse<String>> updateLanguage(@RequestParam Language language,
                                                              HttpServletResponse response) {

        // 1. 쿠키에 저장 (항상 수행)
        Cookie languageCookie = new Cookie("language", language.name());
        languageCookie.setPath("/");
        languageCookie.setHttpOnly(true);
        languageCookie.setMaxAge(7 * 24 * 60 * 60);
        response.addCookie(languageCookie);

        // 2. 로그인 상태라면 DB 업데이트도 수행
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            Long userId = userService.getAuthenticatedUserId();
            userService.updateUserLanguage(userId, language);
        }

        return ResponseEntity.ok(ApiResponse.onSuccess("언어 변경 완료."));
    }
}
