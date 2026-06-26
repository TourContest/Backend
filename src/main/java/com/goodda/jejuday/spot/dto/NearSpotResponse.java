package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.Spot;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class NearSpotResponse {
    private Long id;
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private int likeCount;
    private boolean likedByMe;
    private List<String> imageUrls;

    private Spot.SpotType type;
    private boolean challengeOngoing;

    public static NearSpotResponse fromEntity(Spot spot, int likeCount, boolean likedByMe) {
        List<String> imgs = new ArrayList<>(3);
        if (spot.getImg1() != null && !spot.getImg1().isBlank()) imgs.add(spot.getImg1());
        if (spot.getImg2() != null && !spot.getImg2().isBlank()) imgs.add(spot.getImg2());
        if (spot.getImg3() != null && !spot.getImg3().isBlank()) imgs.add(spot.getImg3());

        // 진행중 챌린지 여부 계산 (Spot.type == CHALLENGE && 오늘이 기간 안)
        boolean ongoing = false;
        if (spot.getType() == Spot.SpotType.CHALLENGE) {
            LocalDate today = LocalDate.now();
            if (spot.getStartDate() != null && spot.getEndDate() != null
                    && !today.isBefore(spot.getStartDate())
                    && !today.isAfter(spot.getEndDate())) {
                ongoing = true;
            }
        }

        return new NearSpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getLatitude(),
                spot.getLongitude(),
                likeCount,
                likedByMe,
                imgs,
                spot.getType(),     // 타입 포함
                ongoing             // 진행중 여부 포함
        );
    }
}