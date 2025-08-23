package com.goodda.jejuday.auth.dto.register.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.Builder;

@Builder
public record UserSummaryResponse(
        Long userId,
        String email,
        String name,
        String nickname,
        Platform platform,
        boolean kakaoLogin,
        Gender gender,
        String birthYear,
        Language language,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        int hallabong,
        long totalSteps,
        Set<String> themes
) {}
