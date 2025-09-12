package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.Spot;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = true)
public class SpotDetailResponse extends SpotResponse {
    private String description;
    private int commentCount;
    private boolean bookmarkedByMe;

    // 추가: 상세 전용 메타
    private Long themeId;        // null 가능
    private String themeName;    // null 가능
    private List<String> tags;   // tag1~3의 name만 담음(존재하는 것만)

    public SpotDetailResponse(Spot spot,
                              int likeCount, boolean likedByMe,
                              boolean bookmarkedByMe) {
        super(spot.getId(), spot.getName(), spot.getLatitude(), spot.getLongitude(), likeCount, likedByMe, buildImageUrls(spot));
        this.description = spot.getDescription();
        this.commentCount = 0;              // 추후 구현
        this.bookmarkedByMe = bookmarkedByMe;

        // 테마
        if (spot.getTheme() != null) {
            this.themeId = spot.getTheme().getId();
            this.themeName = spot.getTheme().getName();
        }

        // 태그들
        this.tags = new ArrayList<>(3);
        if (spot.getTag1() != null && !spot.getTag1().isBlank()) this.tags.add(spot.getTag1());
        if (spot.getTag2() != null && !spot.getTag2().isBlank()) this.tags.add(spot.getTag2());
        if (spot.getTag3() != null && !spot.getTag3().isBlank()) this.tags.add(spot.getTag3());
    }

    private static List<String> buildImageUrls(Spot spot) {
        List<String> imgs = new ArrayList<>(3);
        if (spot.getImg1() != null && !spot.getImg1().isBlank()) imgs.add(spot.getImg1());
        if (spot.getImg2() != null && !spot.getImg2().isBlank()) imgs.add(spot.getImg2());
        if (spot.getImg3() != null && !spot.getImg3().isBlank()) imgs.add(spot.getImg3());
        return imgs;
    }
}