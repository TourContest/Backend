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
    private List<String> imageUrls;
    private int commentCount;
    private boolean bookmarkedByMe;

    public SpotDetailResponse(Spot spot,
                              int likeCount, boolean likedByMe,
                              boolean bookmarkedByMe) {
        super(spot.getId(), spot.getName(), spot.getLatitude(), spot.getLongitude(), likeCount, likedByMe);
        this.description = spot.getDescription();
        this.imageUrls = new ArrayList<>(); // 추후 구현
        this.commentCount = 0;              // 추후 구현
        this.bookmarkedByMe = bookmarkedByMe;
    }
}