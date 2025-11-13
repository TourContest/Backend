package com.goodda.jejuday.crawler.controller;

import com.goodda.jejuday.crawler.entitiy.JejuEvent;
import com.goodda.jejuday.crawler.service.JejuEventCrawlerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/crawler")
public class JejuEventCrawlerController {

    private final JejuEventCrawlerService crawlerService;

    public JejuEventCrawlerController(JejuEventCrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    /**
     * 수동 실행: 현재월(기본) 또는 ?month=MM 지정 → 1페이지만 크롤링 → 진행/예정만 DB 업서트
     * 예) GET /api/crawler/run
     *     GET /api/crawler/run?month=09
     */
    @GetMapping("/run")
    public ResponseEntity<Map<String, Object>> run(
            @RequestParam(value = "month", required = false) String month
    ) throws Exception {
        List<JejuEvent> saved = crawlerService.crawlSingleMonth(month);
        return ResponseEntity.ok(Map.of("count", saved.size()));
    }
}