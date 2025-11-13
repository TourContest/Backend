package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.spot.dto.SpotCommunityResponse;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.service.SearchHistoryService;
import com.goodda.jejuday.spot.service.SpotSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/spots/community")
@RequiredArgsConstructor
public class SpotCommunitySearchController {

    private final SpotSearchService searchService;
    private final SearchHistoryService historyService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<SpotCommunityResponse>>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        historyService.recordSearch(query);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<SpotCommunityResponse> dtoPage = searchService
                .searchCommunitySpotsBySql(query, pageable)
                .map(s -> SpotCommunityResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .description(s.getDescription())
                        .likeCount(s.getLikeCount())
                        .viewCount(s.getViewCount())
                        .type(s.getType())
                        .authorNickname(s.getUser().getNickname())
                        .createdAt(s.getCreatedAt().toString())
                        .build()
                );

        return ResponseEntity.ok(ApiResponse.onSuccess(dtoPage));
    }


    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<String>>> recentHistory() {
        List<String> recent = historyService.getRecentSearchHistory(4);
        return ResponseEntity.ok(ApiResponse.onSuccess(recent));
    }
}
