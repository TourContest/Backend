package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.spot.dto.SpotRecommendationResponse;
import java.util.List;

public interface SpotRecommendationService {
    /** baseSpotId 근처 도보권에서 로그인 유저의 선호 테마와 맞는 상위 3곳을 추천한다. */
    List<SpotRecommendationResponse> recommend(Long baseSpotId);
}
