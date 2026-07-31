package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.spot.dto.SpotRecommendationResponse;
import com.goodda.jejuday.spot.service.SpotRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Spot Recommendation", description = "근처 스팟 추천 API")
@RestController
@RequestMapping("/api/spots")
@RequiredArgsConstructor
public class SpotRecommendationController {

    private final SpotRecommendationService recommendationService;

    @Operation(summary = "근처 스팟 추천 조회", description = "기준 스팟(spotId) 근처에서 로그인 유저의 선호 테마·혼잡도를 반영해 SPOT(공공데이터) 상위 3곳 + CHALLENGE(UGC 승격) 상위 1곳, 총 최대 4곳을 추천합니다. 응답의 type 필드로 SPOT/CHALLENGE를 구분합니다.")
    @GetMapping("/{spotId}/nearby-recommendations")
    public ResponseEntity<ApiResponse<List<SpotRecommendationResponse>>> recommend(@PathVariable Long spotId) {
        return ResponseEntity.ok(ApiResponse.onSuccess(recommendationService.recommend(spotId)));
    }
}
