package com.goodda.jejuday.steps.dto;

public record ExchangeStatusResponse(
        int remainingPoints,        // 남은 교환 가능 포인트
        int remainingExchangeCount, // 남은 교환 횟수
        int todayExchangeCount,     // 오늘 교환 횟수
        int maxDailyExchanges,      // 일일 최대 교환 횟수
        int maxSingleExchange       // 한 번에 최대 교환 포인트
) {}