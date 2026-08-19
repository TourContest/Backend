package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.spot.dto.SpotRecommendationResponse;
import java.util.List;
import java.math.BigDecimal;

public interface SpotRecommendationService {
    /**
     * baseSpotId 근처에서 로그인 유저의 선호 테마와 맞는 스팟을 추천한다.
     * SPOT(공공데이터) 타입 상위 3곳 + CHALLENGE(UGC 승격) 타입 상위 1곳, 총 최대 4곳을 반환한다.
     * 혼잡도가 반영되어 있어 프론트는 이 API 하나만 호출하면 된다.
     */
    List<SpotRecommendationResponse> recommend(Long baseSpotId);

    /** 제주 여부와 관계없이 사용자 현재 좌표를 기준으로 추천한다. */
    List<SpotRecommendationResponse> recommendByLocation(BigDecimal latitude, BigDecimal longitude);
}
