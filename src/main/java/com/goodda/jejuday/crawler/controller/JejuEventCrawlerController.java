package com.goodda.jejuday.crawler.controller;

import com.goodda.jejuday.crawler.service.FestivalSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/crawler")
@RequiredArgsConstructor
public class JejuEventCrawlerController {

    private final FestivalSyncService festivalSyncService;

    @Value("${SYNC_TOKEN:}")
    private String syncToken;

    /** 수동 동기화. 배포 직후 검증이나 긴급 갱신용 */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync(
            @RequestHeader(value = "X-Sync-Token", required = false) String token) {
        if (syncToken == null || syncToken.isBlank() || !syncToken.equals(token)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(Map.of("count", festivalSyncService.syncFestivals()));
    }
}