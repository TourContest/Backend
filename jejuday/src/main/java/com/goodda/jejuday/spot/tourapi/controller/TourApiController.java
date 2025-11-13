package com.goodda.jejuday.spot.tourapi.controller;

import com.goodda.jejuday.spot.tourapi.service.SpotTourSyncService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tour/jeju")
@RequiredArgsConstructor
@Validated
public class TourApiController {

    private final SpotTourSyncService service;

    /** 초기 전체 적재(제주 기본). 권장: arrange=Q(대표이미지+수정일순) */
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

    /** 변경분 동기화. since=yyyyMMdd (예: 20250820), arrange=C(수정일순) */
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
}
