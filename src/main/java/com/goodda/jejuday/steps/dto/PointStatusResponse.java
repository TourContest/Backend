package com.goodda.jejuday.steps.dto;

import com.goodda.jejuday.steps.entity.MoodGrade;

public record PointStatusResponse(
        int hallabong,
        MoodGrade currentGrade
) {}
