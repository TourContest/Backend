package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.spot.dto.SpotCommunityResponse;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.service.SearchHistoryService;
import com.goodda.jejuday.spot.service.SpotSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Spot Community Search", description = "주간제주(커뮤니티) 스팟 검색 API")
@RestController
@RequestMapping("/api/spots/community")
@RequiredArgsConstructor
public class SpotCommunitySearchController {

    private final SpotSearchService searchService;
    private final SearchHistoryService historyService;

    @Operation(summary = "커뮤니티 스팟 검색", description = "검색어(query)로 커뮤니티(주간제주)에 등록된 스팟을 페이징 검색합니다. 검색 시 검색어가 히스토리에 기록됩니다.")
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
                        .title(s.getTitle())
                        .description(s.getDescription())
                        .likeCount(s.getLikeCount())
                        .viewCount(s.getViewCount())
                        .type(s.getType())
                        .authorNickname(s.getUser() != null ? s.getUser().getNickname() : "제주데이")
                        .createdAt(s.getCreatedAt().toString())
                        .build()
                );

        return ResponseEntity.ok(ApiResponse.onSuccess(dtoPage));
    }


    @Operation(summary = "최근 검색어 조회", description = "커뮤니티 스팟 검색의 최근 검색어 최대 4개를 조회합니다.")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<String>>> recentHistory() {
        List<String> recent = historyService.getRecentSearchHistory(4);
        return ResponseEntity.ok(ApiResponse.onSuccess(recent));
    }
}
