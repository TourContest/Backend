package com.goodda.jejuday.steps.dto;

import com.goodda.jejuday.steps.entity.MoodGrade;

public record ConvertPointResponse(
        int convertedPoints,     // 실제 전환된 포인트
        int totalHallabong,      // 전환 후 누적 보유 포인트
        MoodGrade currentGrade,  // 현재 기분 등급
        int remainingToday,       // 오늘 남은 전환 가능 포인트
        int remainingExchangeCount, // 남은 교환 횟수 (추가)
        int todayExchangeCount     // 오늘 교환 횟수 (추가)
) {}
