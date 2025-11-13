package com.goodda.jejuday.spot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.goodda.jejuday.spot.entity.Spot.SpotType;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpotCommunityResponse {
    private Long id;
    private String name;
    private String description;
    private long likeCount;
    private long viewCount;
    private SpotType type;
    private String authorNickname;
    private String createdAt;
}