package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.spot.service.SpotSyncPipelineService;
import com.goodda.jejuday.spot.tourapi.service.TourAnalyticsSyncService;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Spot Admin", description = "스팟 동기화 파이프라인 수동 트리거 (테스트/긴급 갱신용) 관리자 API")
@RestController
@RequestMapping("/api/admin/spots")
@RequiredArgsConstructor
public class SpotAdminController {

    private final SpotSyncPipelineService pipelineService;
    private final TourAnalyticsSyncService analyticsSyncService;

    @Operation(
            summary = "스팟 동기화 파이프라인 수동 실행",
            description = "TourAPI 변경분 동기화 → 상세정보 보강 → 임베딩 생성 → 혼잡도 재계산을 순서대로 실행합니다. "
                    + "매일 새벽 3시(파이프라인)/매시 정각(혼잡도)에 자동 실행되며, 이 API는 배포 직후 검증이나 테스트용 수동 트리거입니다."
    )
    @PostMapping("/sync")
    public SpotSyncPipelineService.PipelineResult syncAll() {
        return pipelineService.runPipeline();
    }

    @Operation(summary = "관광공사 관광지 집중률 수동 동기화",
            description = "관광지 집중률 예측 API를 호출해 오늘 날짜별 혼잡도를 저장합니다.")
    @PostMapping("/congestion/sync")
    public Map<String, Integer> syncCongestion() {
        return Map.of("updated", analyticsSyncService.syncCongestion());
    }
}
