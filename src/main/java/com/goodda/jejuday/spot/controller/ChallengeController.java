package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.spot.dto.*;
import com.goodda.jejuday.spot.service.ChallengeActionService;
import com.goodda.jejuday.spot.service.ChallengeQueryService;
import com.goodda.jejuday.spot.service.ChallengeRecoFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final ChallengeRecoFacade recoFacade;
    private final ChallengeQueryService queryService;
    private final ChallengeActionService actionService;

    // 진행전: 매번 다른 결과 반환
    @GetMapping("/upcoming")
    public ResponseEntity<List<ChallengeResponse>> upcoming() {
        log.info("GET /api/challenges/upcoming called");
        List<ChallengeResponse> result = recoFacade.getUpcomingWithAutoRefresh();
        log.info("Returning {} upcoming challenges", result.size());
        return ResponseEntity.ok(result);
    }

    // 강제 새로고침
    @PostMapping("/upcoming/refresh")
    public ResponseEntity<List<ChallengeResponse>> upcomingRefresh() {
        log.info("POST /api/challenges/upcoming/refresh called");
        List<ChallengeResponse> result = recoFacade.forceRefreshAndGet();
        log.info("Force refresh returned {} challenges", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ongoing")
    public ResponseEntity<List<MyChallengeResponse>> ongoing() {
        log.info("GET /api/challenges/ongoing called");
        List<MyChallengeResponse> result = queryService.ongoingMine();
        log.info("Returning {} ongoing challenges", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/completed")
    public ResponseEntity<List<MyChallengeResponse>> completed(
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        log.info("GET /api/challenges/completed called with sort={}, lastId={}, size={}", sort, lastId, size);
        List<MyChallengeResponse> result = queryService.completedMine(sort, lastId, size);
        log.info("Returning {} completed challenges", result.size());
        return ResponseEntity.ok(result);
    }

    /** 진행 시작 */
    @PostMapping("/{id}/start")
    public ResponseEntity<ChallengeStartResponse> start(
            @PathVariable Long id,
            @RequestBody ChallengeStartRequest req
    ) {
        log.info("POST /api/challenges/{}/start called", id);
        ChallengeStartResponse res = actionService.start(id, req);
        return ResponseEntity.ok(res);
    }

    /** 진행 완료 (근접성 검사 + 포인트 지급) */
    @PostMapping("/{id}/complete")
    public ResponseEntity<ChallengeCompleteResponse> complete(
            @PathVariable Long id,
            @RequestBody ChallengeCompleteRequest req
    ) {
        log.info("POST /api/challenges/{}/complete called", id);
        ChallengeCompleteResponse res = actionService.complete(id, req);
        return ResponseEntity.ok(res);
    }
}