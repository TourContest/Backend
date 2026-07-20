package com.goodda.jejuday.steps.dto;

public record StepConvertRequestDto(
        int requestedPoints,  // 사용자가 교환하고자 하는 포인트 수
        String requestId      // 클라이언트 생성 멱등 키 — 동일 요청 재시도 시 중복 적립 방지
) {}
