package com.goodda.jejuday.spot.tourapi.controller;

import com.goodda.jejuday.spot.tourapi.service.SpotDetailSyncService;
import com.goodda.jejuday.spot.tourapi.service.SpotTourSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Tour API Sync", description = "한국관광공사 TourAPI 제주 스팟 동기화 관리자 API")
@RestController
@RequestMapping("/api/tour/jeju")
@RequiredArgsConstructor
@Validated
public class TourApiController {

    private final SpotTourSyncService service;
    private final SpotDetailSyncService detailSyncService;

    @Operation(summary = "TourAPI 스팟 초기 전체 적재", description = "제주 지역 관광 스팟을 TourAPI에서 초기 전체 적재합니다. 권장값: arrange=Q(대표이미지+수정일순). areaCode 기본값은 제주(39)입니다.")
    @PostMapping("/import")
    public Map<String, Object> importAll(
            @RequestParam(defaultValue = "Q") String arrange,
            @RequestParam(defaultValue = "39") String areaCode,
            @RequestParam(required = false) String lDongRegnCd,
            @RequestParam(required = false) String lDongSignguCd,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int rows
    ) {
        var r = service.initialImport(arrange, areaCode, lDongRegnCd, lDongSignguCd, rows);
        return Map.of("imported", r.imported(), "updated", r.updated(), "skipped", r.skipped(),
                      "pages", r.pages(), "total", r.total());
    }

    @Operation(summary = "TourAPI 스팟 변경분 동기화", description = "since(yyyyMMdd, 예: 20250820) 이후 변경된 스팟을 동기화합니다. 권장값: arrange=C(수정일순).")
    @PostMapping("/sync")
    public Map<String, Object> sync(
            @RequestParam @Pattern(regexp="\\d{8}", message="since는 yyyyMMdd 형식") String since,
            @RequestParam(defaultValue = "C") String arrange,
            @RequestParam(defaultValue = "39") String areaCode,
            @RequestParam(required = false) String lDongRegnCd,
            @RequestParam(required = false) String lDongSignguCd,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int rows
    ) {
        var r = service.syncSince(since, arrange, areaCode, lDongRegnCd, lDongSignguCd, rows);
        return Map.of("imported", r.imported(), "updated", r.updated(), "skipped", r.skipped(),
                      "pages", r.pages(), "total", r.total(), "since", r.since());
    }

    @Operation(summary = "TourAPI 스팟 상세정보 보강 배치", description = "SpotDetail이 없는 SPOT 타입 스팟을 대상으로 개요/이용시간/추가이미지 등 상세정보를 TourAPI에서 가져와 보강합니다.")
    @PostMapping("/detail-sync")
    public Map<String, Object> syncDetails(@RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        var r = detailSyncService.syncAllMissing(limit);
        return Map.of("processed", r.processed(), "updated", r.updated(), "failed", r.failed());
    }
}
