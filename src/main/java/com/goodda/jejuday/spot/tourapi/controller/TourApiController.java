package com.goodda.jejuday.spot.tourapi.controller;

import com.goodda.jejuday.spot.tourapi.service.SpotTourSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 변경분 동기화(/sync)와 상세정보 보강(/detail-sync)은 SpotSyncPipelineService로 통합되어
 * 매일 새벽 자동 실행되고, 수동 테스트는 SpotAdminController#syncAll(POST /api/admin/spots/sync) 하나로 대체됐다.
 * 여기 남은 /import는 최초 1회 전체 적재용 일회성 작업이라 파이프라인에 넣지 않았다.
 */
@Tag(name = "Tour API Sync", description = "한국관광공사 TourAPI 제주 스팟 최초 적재 관리자 API")
@RestController
@RequestMapping("/api/tour/jeju")
@RequiredArgsConstructor
@Validated
public class TourApiController {

    private final SpotTourSyncService service;

    @Operation(summary = "TourAPI 스팟 초기 전체 적재", description = "법정동 코드 기준 관광 스팟을 최초 적재합니다. 기본값은 제주(50)이며 전국 적재는 필터를 비워 호출할 수 있습니다.")
    @PostMapping("/import")
    public Map<String, Object> importAll(
            @RequestParam(defaultValue = "Q") String arrange,
            @RequestParam(required = false) String areaCode,
            @RequestParam(defaultValue = "50") String lDongRegnCd,
            @RequestParam(required = false) String lDongSignguCd,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int rows
    ) {
        var r = service.initialImport(arrange, areaCode, lDongRegnCd, lDongSignguCd, rows);
        return Map.of("imported", r.imported(), "updated", r.updated(), "skipped", r.skipped(),
                      "pages", r.pages(), "total", r.total());
    }
}
