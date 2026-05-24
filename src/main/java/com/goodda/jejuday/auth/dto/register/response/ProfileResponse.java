package com.goodda.jejuday.auth.dto.register.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.steps.entity.MoodGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.Builder;

@Builder
@Schema(description = "내 프로필 상세 응답")
public record ProfileResponse(
        Long userId,
        String email,
        String name,
        String nickname,
        String profile,
        Language language,
        Platform platform,
        Gender gender,
        String birthYear,
        Set<String> themes,
        boolean notificationEnabled,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        int hallabong,
        long totalSteps,
        MoodGrade moodGrade
) {}