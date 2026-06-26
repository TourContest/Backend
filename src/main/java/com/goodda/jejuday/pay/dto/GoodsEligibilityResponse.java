package com.goodda.jejuday.pay.dto;

public record GoodsEligibilityResponse(
        boolean canPurchaseGoods,    // 굿즈 구매 가능 여부
        long stepsNeeded,           // 굿즈 구매까지 필요한 걸음수
        String message              // 안내 메시지
) {}