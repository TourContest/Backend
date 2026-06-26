package com.goodda.jejuday.auth.dto.login.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "로그인 상태 응답")
public record LoginStatusResponse(
        boolean authenticated,
        String provider,   // "APP" | "KAKAO" | null
        Long userId,
        String email,
        String nickname
) {}