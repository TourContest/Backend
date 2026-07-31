package com.goodda.jejuday.spot.tourapi.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * detailIntro2는 contentTypeId별로 필드명이 다르다(예: 관광지=usetime, 문화시설=usetimeculture).
 * 우리가 필요한 정보(이용시간/휴무일/주차)만 contentTypeId 변형 키들을 순서대로 조회해 뽑는다.
 */
public record TourDetailIntro(String useTime, String restDate, String parking) {

    private static final String[] USE_TIME_KEYS = {
            "usetime", "usetimeculture", "usetimefestival", "usetimeleports", "usetimeshopping"
    };
    private static final String[] REST_DATE_KEYS = {
            "restdate", "restdateculture", "restdatefestival", "restdateleports", "restdateshopping"
    };
    private static final String[] PARKING_KEYS = {
            "parking", "parkingculture", "parkingfestival", "parkingleports", "parkingshopping", "parkinglodging"
    };

    public static TourDetailIntro from(JsonNode root) {
        String code = root.path("response").path("header").path("resultCode").asText("");
        if (!"0000".equals(code)) {
            throw new IllegalStateException("TourAPI detailIntro2 error: " + code + " "
                    + root.path("response").path("header").path("resultMsg").asText(""));
        }
        JsonNode item = firstItem(root);
        return new TourDetailIntro(
                firstText(item, USE_TIME_KEYS),
                firstText(item, REST_DATE_KEYS),
                firstText(item, PARKING_KEYS)
        );
    }

    private static JsonNode firstItem(JsonNode root) {
        JsonNode itemNode = root.path("response").path("body").path("items").path("item");
        return itemNode.isArray() ? itemNode.path(0) : itemNode;
    }

    private static String firstText(JsonNode item, String[] keys) {
        for (String key : keys) {
            String v = item.path(key).asText(null);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
