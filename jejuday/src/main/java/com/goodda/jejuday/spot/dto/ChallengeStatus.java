package com.goodda.jejuday.spot.dto;

public enum ChallengeStatus {
    UPCOMING,   // 진행전 (today < startDate)
    ONGOING,    // 진행중 (startDate <= today <= endDate or endDate == null)
    COMPLETED   // 완료   (endDate < today)
}