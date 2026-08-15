package com.goodda.jejuday.mission.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.mission.dto.MissionStepResponse;
import com.goodda.jejuday.mission.dto.MissionThemeResponse;
import com.goodda.jejuday.mission.service.MissionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Mission", description = "테마 미션(스탬프 투어) 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/missions")
public class MissionController {

    private final MissionQueryService missionQueryService;

    @Operation(summary = "미션 테마 목록 조회", description = "전체 미션 테마와 로그인한 유저의 진행도(완료 스텝 수, 완주 여부)를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MissionThemeResponse>>> getMissions() {
        return ResponseEntity.ok(ApiResponse.onSuccess(missionQueryService.getMissionThemes()));
    }

    @Operation(summary = "미션 테마 상세(스텝 목록) 조회", description = "특정 미션 테마에 속한 스텝(스팟) 목록과 각 스텝의 완료 여부를 조회합니다. 스탬프 카드 UI용.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<List<MissionStepResponse>>> getMissionSteps(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.onSuccess(missionQueryService.getMissionSteps(id)));
    }
}
