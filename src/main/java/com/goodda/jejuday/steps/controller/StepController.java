package com.goodda.jejuday.steps.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.security.CustomUserDetails;
import com.goodda.jejuday.steps.dto.ConvertPointResponse;
import com.goodda.jejuday.steps.dto.ExchangeStatusResponse;
import com.goodda.jejuday.steps.dto.PointStatusResponse;
import com.goodda.jejuday.steps.dto.StepConvertRequestDto;
import com.goodda.jejuday.steps.dto.StepRequestDto;
import com.goodda.jejuday.steps.entity.MoodGrade;
import com.goodda.jejuday.steps.service.StepService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/steps")
@RequiredArgsConstructor
public class StepController {

    private final StepService stepService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> uploadSteps(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody StepRequestDto request) {

        stepService.recordSteps(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.onSuccessVoid("걸음수가 성공적으로 등록되었습니다."));
    }

    @PostMapping("/convert")
    public ResponseEntity<ApiResponse<ConvertPointResponse>> convertStepsToPoints(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody StepConvertRequestDto request) {

        int converted = stepService.convertStepsToPoints(user.getUserId(), request.requestedPoints());
        User u = userRepository.findById(user.getUserId()).orElseThrow();

        int remaining = stepService.getRemainingConvertiblePoints(u);
        int remainingExchangeCount = stepService.getRemainingExchangeCount(user.getUserId());
        int todayExchangeCount = stepService.getTodayExchangeCount(user.getUserId());

        ConvertPointResponse response = new ConvertPointResponse(
                converted,
                u.getHallabong(),
                u.getMoodGrade(),
                remaining,
                remainingExchangeCount, // 남은 교환 횟수
                todayExchangeCount     // 오늘 교환 횟수
        );

        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @GetMapping("/reward/received")
    public ResponseEntity<ApiResponse<Set<MoodGrade>>> getReceivedRewards(
            @AuthenticationPrincipal CustomUserDetails user) {

        Set<MoodGrade> result = stepService.getReceivedRewardGrades(user.getUserId());
        return ResponseEntity.ok(ApiResponse.onSuccess(result));
    }

    @GetMapping("/point")
    public ResponseEntity<ApiResponse<PointStatusResponse>> getPointStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        PointStatusResponse result = stepService.getPointStatus(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.onSuccess(result));
    }

    // 시작 보너스 수동 적용 API
    @PostMapping("/start-bonus")
    public ResponseEntity<ApiResponse<Long>> applyStartBonus(
            @AuthenticationPrincipal CustomUserDetails user) {

        long bonusSteps = stepService.applyDailyStartBonus(user.getUserId());
        String message = bonusSteps > 0
                ? String.format("시작 보너스 %d보가 적용되었습니다!", bonusSteps)
                : "이미 시작 보너스가 적용되었거나 대상이 아닙니다.";

        return ResponseEntity.ok(ApiResponse.onSuccess(bonusSteps, message));
    }

    // 시작 보너스 적용 가능 여부 확인
    @GetMapping("/start-bonus/available")
    public ResponseEntity<ApiResponse<Boolean>> canApplyStartBonus(
            @AuthenticationPrincipal CustomUserDetails user) {

        boolean canApply = stepService.canApplyStartBonus(user.getUserId());
        return ResponseEntity.ok(ApiResponse.onSuccess(canApply));
    }

    // 오늘의 시작 보너스 조회
    @GetMapping("/start-bonus/today")
    public ResponseEntity<ApiResponse<Long>> getTodayStartBonus(
            @AuthenticationPrincipal CustomUserDetails user) {

        long todayBonus = stepService.getTodayStartBonus(user.getUserId());
        return ResponseEntity.ok(ApiResponse.onSuccess(todayBonus));
    }

    // 교환 제한 정보 조회 API 추가
    @GetMapping("/exchange/status")
    public ResponseEntity<ApiResponse<ExchangeStatusResponse>> getExchangeStatus(
            @AuthenticationPrincipal CustomUserDetails user) {

        User u = userRepository.findById(user.getUserId()).orElseThrow();
        int remainingPoints = stepService.getRemainingConvertiblePoints(u);
        int remainingExchangeCount = stepService.getRemainingExchangeCount(user.getUserId());
        int todayExchangeCount = stepService.getTodayExchangeCount(user.getUserId());

        ExchangeStatusResponse response = new ExchangeStatusResponse(
                remainingPoints,
                remainingExchangeCount,
                todayExchangeCount,
                20, // 최대 교환 횟수
                100 // 한 번에 최대 교환 포인트
        );

        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
