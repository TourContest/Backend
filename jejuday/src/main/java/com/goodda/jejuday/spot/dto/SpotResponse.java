package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.Spot;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class SpotResponse {
    private Long id;
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private int likeCount;
    private boolean likedByMe;

    public static SpotResponse fromEntity(Spot spot, int likeCount, boolean likedByMe) {
        return new SpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getLatitude(),
                spot.getLongitude(),
                likeCount,
                likedByMe
        );
    }
}