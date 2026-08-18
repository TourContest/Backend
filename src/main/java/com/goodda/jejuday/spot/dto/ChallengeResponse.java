package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.Spot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeResponse {
    private static final int CHALLENGE_POINT = 300;

    private Long id;
    private String name;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String img1; // 추가
    private Long themeId;
    private String themeName;
    private Integer point;
    private Integer viewCount;
    private Integer likeCount;

    public static ChallengeResponse of(Spot spot) {
        if (spot == null) return null;

        return new ChallengeResponse(
                spot.getId(),
                spot.getName(),
                spot.getDescription(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getImg1(), // img1 추가
                spot.getTheme() != null ? spot.getTheme().getId() : null,
                spot.getTheme() != null ? spot.getTheme().getName() : null,
                resolvePoint(spot),
                spot.getViewCount(),
                spot.getLikeCount()
        );
    }

    private static int resolvePoint(Spot spot) {
        return CHALLENGE_POINT;
    }
}
