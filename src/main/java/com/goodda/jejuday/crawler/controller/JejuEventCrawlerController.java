package com.goodda.jejuday.crawler.controller;

import com.goodda.jejuday.crawler.service.FestivalSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Jeju Event Crawler", description = "제주 축제/행사 크롤링 수동 동기화 관리자 API")
@RestController
@RequestMapping("/api/crawler")
@RequiredArgsConstructor
public class JejuEventCrawlerController {

    private final FestivalSyncService festivalSyncService;

    @Value("${SYNC_TOKEN:}")
    private String syncToken;

    @Operation(summary = "제주 축제/행사 수동 동기화", description = "제주 축제·행사 정보를 수동으로 크롤링하여 동기화합니다. 배포 직후 검증이나 긴급 갱신 용도입니다. X-Sync-Token 헤더에 유효한 토큰이 필요하며, 불일치 시 401을 반환합니다.")
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @RequestHeader(value = "X-Sync-Token", required = false) String token) {
        if (syncToken == null || syncToken.isBlank() || !syncToken.equals(token)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(Map.of("count", festivalSyncService.syncFestivals()));
    }
}