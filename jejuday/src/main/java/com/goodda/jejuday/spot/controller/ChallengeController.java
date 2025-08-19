package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.spot.dto.ChallengeResponse;
import com.goodda.jejuday.spot.service.ChallengeQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final ChallengeQueryService service;

    public ChallengeController(ChallengeQueryService service) {
        this.service = service;
    }

    /** 진행전: 랜덤 1개 */
    @GetMapping("/upcoming")
    public ResponseEntity<ChallengeResponse> upcomingRandom() {
        return ResponseEntity.ok(service.getUpcomingRandom());
    }

    /** 진행중: lastId(옵션), size(옵션) 기반 무한스크롤 */
    @GetMapping("/ongoing")
    public ResponseEntity<List<ChallengeResponse>> ongoing(
            @RequestParam(required = false) Long lastId,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(service.getOngoing(lastId, size));
    }

    /** 완료: lastId(옵션), size(옵션) 기반 무한스크롤 */
    @GetMapping("/completed")
    public ResponseEntity<List<ChallengeResponse>> completed(
            @RequestParam(required = false) Long lastId,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(service.getCompleted(lastId, size));
    }
}