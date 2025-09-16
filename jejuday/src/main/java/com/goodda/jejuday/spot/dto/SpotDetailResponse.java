package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.Spot;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = true)
public class SpotDetailResponse extends SpotResponse {

    private String description;
    private int commentCount;
    private boolean bookmarkedByMe;

    // 상세 전용 메타
    private Long themeId;        // null 가능
    private String themeName;    // null 가능
    private List<String> tags;   // tag1~3 존재하는 것만
    private LocalDateTime updatedAt;

    // ✅ 최신 시그니처
    public SpotDetailResponse(Spot spot,
                              int likeCount,
                              boolean likedByMe,
                              boolean bookmarkedByMe,
                              boolean challengeOngoing,
                              String authorNickname,
                              int commentCount) {
        super(
                spot.getId(),
                spot.getName(),
                spot.getLatitude(),
                spot.getLongitude(),
                likeCount,
                likedByMe,
                buildImageUrls(spot),
                spot.getType(),
                challengeOngoing,
                authorNickname,
                spot.getCreatedAt() != null ? spot.getCreatedAt().withNano(0) : null,
                spot.getViewCount() == null ? 0 : spot.getViewCount()
        );
        this.description = spot.getDescription();
        this.commentCount = commentCount;                       // ✅ 전달값 반영
        this.bookmarkedByMe = bookmarkedByMe;
        this.updatedAt = spot.getUpdatedAt() != null ? spot.getUpdatedAt().withNano(0) : null;

        if (spot.getTheme() != null) {
            this.themeId = spot.getTheme().getId();
            this.themeName = spot.getTheme().getName();
        }

        this.tags = new ArrayList<>(3);
        if (spot.getTag1() != null && !spot.getTag1().isBlank()) this.tags.add(spot.getTag1());
        if (spot.getTag2() != null && !spot.getTag2().isBlank()) this.tags.add(spot.getTag2());
        if (spot.getTag3() != null && !spot.getTag3().isBlank()) this.tags.add(spot.getTag3());
    }

    // ✅ 과거 서비스 호출 호환용 (예: new SpotDetailResponse(spot, likeCount, liked, bookmarked))
    public SpotDetailResponse(Spot spot,
                              int likeCount,
                              boolean likedByMe,
                              boolean bookmarkedByMe) {
        this(spot, likeCount, likedByMe, bookmarkedByMe,
                false,
                (spot.getUser() != null ? spot.getUser().getNickname() : null),
                0);                                   // commentCount 기본 0
    }

    private static List<String> buildImageUrls(Spot spot) {
        List<String> imgs = new ArrayList<>(3);
        if (spot.getImg1() != null && !spot.getImg1().isBlank()) imgs.add(spot.getImg1());
        if (spot.getImg2() != null && !spot.getImg2().isBlank()) imgs.add(spot.getImg2());
        if (spot.getImg3() != null && !spot.getImg3().isBlank()) imgs.add(spot.getImg3());
        return imgs;
    }
}
