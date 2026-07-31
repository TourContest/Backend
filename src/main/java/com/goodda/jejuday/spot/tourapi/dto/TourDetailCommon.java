package com.goodda.jejuday.spot.tourapi.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record TourDetailCommon(String overview, String homepage) {

    public static TourDetailCommon from(JsonNode root) {
        String code = root.path("response").path("header").path("resultCode").asText("");
        if (!"0000".equals(code)) {
            throw new IllegalStateException("TourAPI detailCommon2 error: " + code + " "
                    + root.path("response").path("header").path("resultMsg").asText(""));
        }
        JsonNode item = firstItem(root);
        return new TourDetailCommon(
                item.path("overview").asText(null),
                item.path("homepage").asText(null)
        );
    }

    private static JsonNode firstItem(JsonNode root) {
        JsonNode itemNode = root.path("response").path("body").path("items").path("item");
        return itemNode.isArray() ? itemNode.path(0) : itemNode;
    }
}
