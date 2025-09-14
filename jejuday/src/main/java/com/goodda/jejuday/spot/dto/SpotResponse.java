package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.Spot;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class SpotResponse {
    private Long id;
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private int likeCount;
    private boolean likedByMe;
    private List<String> imageUrls;

    public static SpotResponse fromEntity(Spot spot, int likeCount, boolean likedByMe) {
        List<String> imgs = new ArrayList<>(3);
        if (spot.getImg1() != null && !spot.getImg1().isBlank()) imgs.add(spot.getImg1());
        if (spot.getImg2() != null && !spot.getImg2().isBlank()) imgs.add(spot.getImg2());
        if (spot.getImg3() != null && !spot.getImg3().isBlank()) imgs.add(spot.getImg3());
        return new SpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getLatitude(),
                spot.getLongitude(),
                likeCount,
                likedByMe,
                imgs
        );
    }
}