package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.spot.dto.SpotCommunityResponse;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.service.SearchHistoryService;
import com.goodda.jejuday.spot.service.SpotSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
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
        // 진단용: 실제 서버에 도착한 query 원문을 바이트 단위로 확인하기 위한 로그.
        // (더블 인코딩/깨진 문자 등 클라이언트-서버 간 전송 문제를 가리기 위함 — 원인 확인되면 제거)
        log.info("커뮤니티 검색 요청: query='{}' (len={}, utf8Hex={})",
                query, query.length(),
                HexFormat.of().formatHex(query.getBytes(StandardCharsets.UTF_8)));

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
                        .imageUrls(s.getImageUrls())
                        .build()
                );

        log.info("커뮤니티 검색 결과: query='{}' totalElements={}", query, dtoPage.getTotalElements());

        return ResponseEntity.ok(ApiResponse.onSuccess(dtoPage));
    }


    @Operation(summary = "최근 검색어 조회", description = "커뮤니티 스팟 검색의 최근 검색어 최대 4개를 조회합니다.")
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<String>>> recentHistory() {
        List<String> recent = historyService.getRecentSearchHistory(4);
        return ResponseEntity.ok(ApiResponse.onSuccess(recent));
    }
}
