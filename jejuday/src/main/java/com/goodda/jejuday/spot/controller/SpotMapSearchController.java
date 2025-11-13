package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.dto.SpotMapResponse;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.service.SearchHistoryService;
import com.goodda.jejuday.spot.service.SpotSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.goodda.jejuday.auth.util.SecurityUtil;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/spots/map")
@RequiredArgsConstructor
public class SpotMapSearchController {

    private final SpotSearchService searchService;
    private final SearchHistoryService historyService;

    // TODO : 갯수 제한 or 거리에 가까운 순으로 띄우기
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

    /** 최근 4개 검색어 반환 */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<String>>> recentHistory() {
        List<String> recent = historyService.getRecentSearchHistory(4);
        return ResponseEntity.ok(ApiResponse.onSuccess(recent));
    }
}
