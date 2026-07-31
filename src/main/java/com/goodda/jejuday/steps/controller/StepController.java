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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Step", description = "걸음수 기록/포인트 전환/보너스 API")
@RestController
@RequestMapping("/v1/steps")
@RequiredArgsConstructor
public class StepController {

    private final StepService stepService;
    private final UserRepository userRepository;

    /** 비로그인 요청은 SecurityConfig가 필터 단에서 걸러주지 않으므로, 컨트롤러에서 직접 방어한다. */
    private Long requireUserId(CustomUserDetails principal) {
        if (principal == null) {
            throw new IllegalArgumentException("인증된 사용자가 아닙니다.");
        }
        return principal.getUserId();
    }

    @Operation(summary = "걸음수 등록", description = "유저의 걸음수를 기록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> uploadSteps(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody StepRequestDto request) {

        stepService.recordSteps(requireUserId(user), request);
        return ResponseEntity.ok(ApiResponse.onSuccessVoid("걸음수가 성공적으로 등록되었습니다."));
    }

    @Operation(summary = "걸음수 포인트 전환", description = "누적된 걸음수를 한라봉 포인트(hallabong)로 전환합니다. requestedPoints로 요청 포인트를, requestId로 중복 요청을 방지합니다. 일일/1회 전환 한도가 적용됩니다.")
    @PostMapping("/convert")
    public ResponseEntity<ApiResponse<ConvertPointResponse>> convertStepsToPoints(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody StepConvertRequestDto request) {

        Long userId = requireUserId(user);
        int converted = stepService.convertStepsToPoints(userId, request.requestedPoints(), request.requestId());
        User u = userRepository.findById(userId).orElseThrow();

        int remaining = stepService.getRemainingConvertiblePoints(u);
        int remainingExchangeCount = stepService.getRemainingExchangeCount(userId);
        int todayExchangeCount = stepService.getTodayExchangeCount(userId);

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

    @Operation(summary = "받은 보상 등급 조회", description = "유저가 지금까지 받은 무드 등급(MoodGrade) 보상 목록을 조회합니다.")
    @GetMapping("/reward/received")
    public ResponseEntity<ApiResponse<Set<MoodGrade>>> getReceivedRewards(
            @AuthenticationPrincipal CustomUserDetails user) {

        Set<MoodGrade> result = stepService.getReceivedRewardGrades(requireUserId(user));
        return ResponseEntity.ok(ApiResponse.onSuccess(result));
    }

    @Operation(summary = "포인트 현황 조회", description = "유저의 현재 포인트(한라봉) 및 걸음수 관련 상태를 조회합니다.")
    @GetMapping("/point")
    public ResponseEntity<ApiResponse<PointStatusResponse>> getPointStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        PointStatusResponse result = stepService.getPointStatus(requireUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.onSuccess(result));
    }

    @Operation(summary = "시작 보너스 수동 적용", description = "오늘 하루 시작 보너스 걸음수를 수동으로 적용합니다. 이미 적용되었거나 대상이 아니면 0을 반환합니다.")
    @PostMapping("/start-bonus")
    public ResponseEntity<ApiResponse<Long>> applyStartBonus(
            @AuthenticationPrincipal CustomUserDetails user) {

        long bonusSteps = stepService.applyDailyStartBonus(requireUserId(user));
        String message = bonusSteps > 0
                ? String.format("시작 보너스 %d보가 적용되었습니다!", bonusSteps)
                : "이미 시작 보너스가 적용되었거나 대상이 아닙니다.";

        return ResponseEntity.ok(ApiResponse.onSuccess(bonusSteps, message));
    }

    @Operation(summary = "시작 보너스 적용 가능 여부 조회", description = "오늘 하루 시작 보너스를 아직 적용받을 수 있는지 여부를 조회합니다.")
    @GetMapping("/start-bonus/available")
    public ResponseEntity<ApiResponse<Boolean>> canApplyStartBonus(
            @AuthenticationPrincipal CustomUserDetails user) {

        boolean canApply = stepService.canApplyStartBonus(requireUserId(user));
        return ResponseEntity.ok(ApiResponse.onSuccess(canApply));
    }

    @Operation(summary = "오늘의 시작 보너스 조회", description = "오늘 적용된 시작 보너스 걸음수를 조회합니다.")
    @GetMapping("/start-bonus/today")
    public ResponseEntity<ApiResponse<Long>> getTodayStartBonus(
            @AuthenticationPrincipal CustomUserDetails user) {

        long todayBonus = stepService.getTodayStartBonus(requireUserId(user));
        return ResponseEntity.ok(ApiResponse.onSuccess(todayBonus));
    }

    @Operation(summary = "교환 제한 정보 조회", description = "남은 전환 가능 포인트, 남은/오늘 교환 횟수, 교환 한도(최대 20회, 1회 최대 100포인트)를 조회합니다.")
    @GetMapping("/exchange/status")
    public ResponseEntity<ApiResponse<ExchangeStatusResponse>> getExchangeStatus(
            @AuthenticationPrincipal CustomUserDetails user) {

        Long userId = requireUserId(user);
        User u = userRepository.findById(userId).orElseThrow();
        int remainingPoints = stepService.getRemainingConvertiblePoints(u);
        int remainingExchangeCount = stepService.getRemainingExchangeCount(userId);
        int todayExchangeCount = stepService.getTodayExchangeCount(userId);

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
