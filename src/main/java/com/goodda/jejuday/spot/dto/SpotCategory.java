package com.goodda.jejuday.spot.dto;

/**
 * TourAPI content_type_id 기반 3분류. 유저가 직접 쓴 글(POST/SPOT)은 content_type_id가
 * 비어있어 UNIQUE_SPOT으로 편입된다 - 글쓰기 플로우에 카테고리 선택 UI가 없어서다.
 */
public enum SpotCategory {
    TOURIST_SPOT, RESTAURANT, UNIQUE_SPOT;

    public static SpotCategory fromContentTypeId(String contentTypeId) {
        if ("12".equals(contentTypeId)) return TOURIST_SPOT;
        if ("39".equals(contentTypeId)) return RESTAURANT;
        return UNIQUE_SPOT;
    }
}
