package com.goodda.jejuday.steps.dto;

import com.goodda.jejuday.steps.entity.MoodGrade;

public record MoodRewardResponse(
        int rewardAmount,
        int totalHallabong,
        MoodGrade currentGrade
) {}
