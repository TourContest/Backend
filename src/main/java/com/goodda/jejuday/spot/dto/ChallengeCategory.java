package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.Spot;
import java.util.Locale;
import java.util.Set;

public record ChallengeCategory(String code, String label) {
    private static final Set<String> TOUR_TYPES = Set.of("12", "14", "15", "25", "28");
    private static final String[] CAFE_WORDS = {"카페", "커피", "로스터리", "베이커리", "티룸", "coffee", "cafe"};

    public static ChallengeCategory from(Spot spot) {
        String contentType = spot.getContentTypeId();
        if ("39".equals(contentType)) {
            String name = spot.getName() == null ? "" : spot.getName().toLowerCase(Locale.ROOT);
            for (String word : CAFE_WORDS) {
                if (name.contains(word)) return new ChallengeCategory("CAFE", "카페");
            }
            return new ChallengeCategory("RESTAURANT", "식당");
        }
        if (TOUR_TYPES.contains(contentType)) return new ChallengeCategory("TOURIST_ATTRACTION", "관광지");
        return new ChallengeCategory("OTHER", "기타");
    }
}
