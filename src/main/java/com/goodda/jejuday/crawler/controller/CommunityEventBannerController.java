package com.goodda.jejuday.crawler.controller;

import com.goodda.jejuday.crawler.dto.CommunityEventBannerDto;
import com.goodda.jejuday.crawler.service.CommunityEventBannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/community/events")
@Tag(name = "Community", description = "커뮤니티 섹션 공개 API")
public class CommunityEventBannerController {

    private final CommunityEventBannerService service;

    @GetMapping("/banner")
    @Operation(
            summary = "커뮤니티 배너 행사 목록",
            description = "홈 > 커뮤니티 탭 배너에 노출. 오늘 기준(또는 지정 날짜)에 진행 중인 행사만 반환합니다."
    )
    public List<CommunityEventBannerDto> getBanners(
            @Parameter(description = "기준 날짜 (미지정 시 오늘)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        if (date == null) date = LocalDate.now();
        return service.findBanners(date);
    }
}
