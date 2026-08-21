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
    /** "PREF_THEME"(선호 테마로 뽑힘) | "BACKFILL_RANDOM"(테마 매칭 없이 채워짐) | null(추천 컨텍스트 없음) */
    private String recommendReason;

    /** 프론트 신규 규격과 기존 img1 규격을 동시에 지원한다. */
    public String getImageUrl() {
        return img1;
    }

    public static ChallengeResponse of(Spot spot) {
        return of(spot, null);
    }

    public static ChallengeResponse of(Spot spot, String recommendReason) {
        if (spot == null) return null;

        return new ChallengeResponse(
                spot.getId(),
                spot.getName(),
                spot.getDescription(),
                spot.getLatitude(),
                spot.getLongitude(),
                resolveImageUrl(spot),
                spot.getTheme() != null ? spot.getTheme().getId() : null,
                spot.getTheme() != null ? spot.getTheme().getName() : null,
                resolvePoint(spot),
                spot.getViewCount(),
                spot.getLikeCount(),
                recommendReason
        );
    }

    private static int resolvePoint(Spot spot) {
        return CHALLENGE_POINT;
    }

    private static String resolveImageUrl(Spot spot) {
        String url = firstPresent(spot.getImg1(), spot.getImg2(), spot.getImg3());
        return url != null && url.startsWith("http://") ? "https://" + url.substring(7) : url;
    }

    private static String firstPresent(String... urls) {
        for (String url : urls) {
            if (url != null && !url.isBlank()) return url;
        }
        return null;
    }
}
