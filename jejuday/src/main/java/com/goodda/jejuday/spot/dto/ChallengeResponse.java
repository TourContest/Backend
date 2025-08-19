package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.Spot;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ChallengeResponse(
        Long id,
        String name,
        String description,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDate startDate,
        LocalDate endDate,
        Integer point,
        Integer viewCount,
        Integer likeCount,
        ChallengeStatus status
) {
    public static ChallengeResponse from(Spot s, ChallengeStatus status) {
        return new ChallengeResponse(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.getLatitude(),
                s.getLongitude(),
                s.getStartDate(),
                s.getEndDate(),
                s.getPoint(),
                s.getViewCount(),
                s.getLikeCount(),
                status
        );
    }
}