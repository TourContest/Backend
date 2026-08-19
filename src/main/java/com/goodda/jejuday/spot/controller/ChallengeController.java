package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.spot.dto.*;
import com.goodda.jejuday.spot.service.ChallengeActionService;
import com.goodda.jejuday.spot.service.ChallengeQueryService;
import com.goodda.jejuday.spot.service.ChallengeRecoFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "Challenge", description = "챌린지 추천/진행/인증/완료 API")
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final ChallengeRecoFacade recoFacade;
    private final ChallengeQueryService queryService;
    private final ChallengeActionService actionService;

    @Operation(summary = "다가오는 챌린지 추천 목록 조회", description = "매번 다른 추천 결과를 반환합니다. 마지막 갱신 후 일정 시간이 지나면 자동으로 새로고침됩니다.")
    @GetMapping("/upcoming")
    public ResponseEntity<List<ChallengeResponse>> upcoming() {
        log.info("GET /api/challenges/upcoming called");
        List<ChallengeResponse> result = recoFacade.getUpcomingWithAutoRefresh();
        log.info("Returning {} upcoming challenges", result.size());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "다가오는 챌린지 추천 강제 새로고침", description = "캐시된 추천 결과와 무관하게 챌린지 추천 목록을 강제로 다시 계산하여 반환합니다.")
    @PostMapping("/upcoming/refresh")
    public ResponseEntity<List<ChallengeResponse>> upcomingRefresh() {
        log.info("POST /api/challenges/upcoming/refresh called");
        List<ChallengeResponse> result = recoFacade.forceRefreshAndGet();
        log.info("Force refresh returned {} challenges", result.size());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "진행 중인 내 챌린지 조회", description = "로그인한 유저가 현재 진행 중인 챌린지 목록을 조회합니다.")
    @GetMapping("/ongoing")
    public ResponseEntity<List<MyChallengeResponse>> ongoing() {
        log.info("GET /api/challenges/ongoing called");
        List<MyChallengeResponse> result = queryService.ongoingMine();
        log.info("Returning {} ongoing challenges", result.size());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "완료한 내 챌린지 조회", description = "로그인한 유저가 완료한 챌린지 목록을 커서 기반(lastId)으로 페이징 조회합니다. sort는 정렬 기준(기본 latest)입니다.")
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

    @Operation(summary = "챌린지 진행 시작", description = "선택한 챌린지의 진행을 시작합니다.")
    @PostMapping("/{id}/start")
    public ResponseEntity<ChallengeStartResponse> start(
            @PathVariable Long id,
            @RequestBody ChallengeStartRequest req
    ) {
        log.info("POST /api/challenges/{}/start called", id);
        ChallengeStartResponse res = actionService.start(id, req);
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "방문 인증 사진 업로드", description = "챌린지 방문 인증 사진을 업로드합니다. 응답으로 받은 proofUrl을 이후 complete() 요청 바디에 담아 전송해야 합니다.")
    @PostMapping(value = "/{id}/proof-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadProofImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        String url = actionService.uploadProofImage(id, file);
        return ResponseEntity.ok(Map.of("proofUrl", url));
    }

    @Operation(summary = "챌린지 진행 완료", description = "챌린지를 완료 처리합니다. 서버에서 위치 근접성 검사를 수행한 뒤 통과 시 포인트를 지급합니다.")
    @PostMapping("/{id}/complete")
    public ResponseEntity<ChallengeCompleteResponse> complete(
            @PathVariable Long id,
            @RequestBody ChallengeCompleteRequest req
    ) {
        log.info("POST /api/challenges/{}/complete called", id);
        ChallengeCompleteResponse res = actionService.complete(id, req);
        return ResponseEntity.ok(res);
    }

    @Operation(summary = "챌린지 진행 취소", description = "진행중(JOINED/SUBMITTED/APPROVED)인 챌린지 참여를 취소합니다. 이미 완료/취소/거절된 참여는 취소할 수 없습니다.")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        log.info("POST /api/challenges/{}/cancel called", id);
        actionService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
