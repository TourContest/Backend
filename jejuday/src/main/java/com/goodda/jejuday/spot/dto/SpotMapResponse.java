package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.Spot.SpotType;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpotMapResponse {
    private Long id;
    private String name;
    private double latitude;
    private double longitude;
    private SpotType type;
}