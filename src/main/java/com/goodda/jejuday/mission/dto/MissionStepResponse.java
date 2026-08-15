package com.goodda.jejuday.mission.dto;

import com.goodda.jejuday.mission.entity.MissionStep;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MissionStepResponse {
    private Long spotId;
    private String stepLabel;
    private int order;
    private boolean isCompleted;

    public static MissionStepResponse of(MissionStep step, boolean isCompleted) {
        return new MissionStepResponse(
                step.getSpot().getId(),
                step.getStepLabel(),
                step.getStepOrder(),
                isCompleted
        );
    }
}
