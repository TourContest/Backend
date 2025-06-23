package com.goodda.jejuday.Auth.controller;

import com.goodda.jejuday.Auth.dto.ApiResponse;
import com.goodda.jejuday.Auth.dto.KakaoDTO;
import com.goodda.jejuday.Auth.dto.register.request.FinalAppRegisterRequest;
import com.goodda.jejuday.Auth.entity.Language;
import com.goodda.jejuday.Auth.entity.Platform;
import com.goodda.jejuday.Auth.service.KakaoService;
import com.goodda.jejuday.Auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/login")
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

    @GetMapping("/callback")
    public String handleKakaoCallback(
            @RequestParam("code") String code) {
        return "redirect:/kakao-join?code=" + code;
    }
}

