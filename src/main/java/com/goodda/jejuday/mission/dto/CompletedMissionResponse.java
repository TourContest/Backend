package com.goodda.jejuday.mission.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CompletedMissionResponse {
    private Long missionId;
    private String title;
}
