package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.dto.SpotMapResponse;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.service.SearchHistoryService;
import com.goodda.jejuday.spot.service.SpotSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.goodda.jejuday.auth.util.SecurityUtil;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Spot Map Search", description = "홈 지도 화면 스팟 검색 API")
@RestController
@RequestMapping("/api/spots/map")
@RequiredArgsConstructor
public class SpotMapSearchController {

    private final SpotSearchService searchService;
    private final SearchHistoryService historyService;
    private final com.goodda.jejuday.spot.service.SpotNearbyMapService nearbyMapService;

    // TODO : 갯수 제한 or 거리에 가까운 순으로 띄우기
    @Operation(summary = "지도 스팟 검색", description = "검색어(query)로 지도에 표시할 스팟을 트라이(Trie) 기반으로 검색합니다. 검색 시 검색어가 히스토리에 기록됩니다.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<SpotMapResponse>>> search(@RequestParam String query) {
        // 서비스 레이어에서 한 번만 SecurityUtil 호출
        historyService.recordSearch(query);

        List<SpotMapResponse> result = searchService.searchMapSpotsByTrie(query).stream()
                .map(s -> SpotMapResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .latitude(s.getLatitude().doubleValue())
                        .longitude(s.getLongitude().doubleValue())
                        .type(s.getType())
                        .build()
                )
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.onSuccess(result));
    }

    @Operation(summary = "최근 검색어 조회", description = "지도 스팟 검색의 최근 검색어 최대 4개를 조회합니다.")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<String>>> recentHistory() {
        List<String> recent = historyService.getRecentSearchHistory(4);
        return ResponseEntity.ok(ApiResponse.onSuccess(recent));
    }

    @Operation(summary = "현재 위치 주변 지도 마커", description = "전국 어디서든 현재 위치 반경 내 SPOT/CHALLENGE를 거리순으로 최대 100개 반환합니다. 공식 관광지가 부족하면 TourAPI 위치기반 데이터를 캐시합니다.")
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<SpotMapResponse>>> nearby(
            @RequestParam java.math.BigDecimal latitude,
            @RequestParam java.math.BigDecimal longitude,
            @RequestParam(defaultValue = "10") int radiusKm) {
        if (latitude.compareTo(java.math.BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(java.math.BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(java.math.BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(java.math.BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("올바른 위도·경도가 아닙니다.");
        }
        List<SpotMapResponse> result = nearbyMapService.find(latitude, longitude, radiusKm).stream()
                .map(s -> SpotMapResponse.builder().id(s.getId()).name(s.getName())
                        .latitude(s.getLatitude().doubleValue()).longitude(s.getLongitude().doubleValue())
                        .type(s.getType()).build()).toList();
        return ResponseEntity.ok(ApiResponse.onSuccess(result));
    }
}
