package com.goodda.jejuday.mission.dto;

import com.goodda.jejuday.mission.entity.MissionTheme;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MissionThemeResponse {
    private Long id;
    private String title;
    private String description;
    private String coverImageUrl;
    private int totalSteps;
    private int completedSteps;
    private boolean isCompleted;

    public static MissionThemeResponse of(MissionTheme theme, int completedSteps, boolean isCompleted) {
        return new MissionThemeResponse(
                theme.getId(),
                theme.getTitle(),
                theme.getDescription(),
                theme.getCoverImageUrl(),
                theme.getTotalSteps(),
                completedSteps,
                isCompleted
        );
    }
}
