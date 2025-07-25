package com.goodda.jejuday.steps.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.security.CustomUserDetails;
import com.goodda.jejuday.steps.dto.ConvertPointResponse;
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
        return ResponseEntity.ok(ApiResponse.onSuccess(null));
    }

    @PostMapping("/convert")
    public ResponseEntity<ApiResponse<ConvertPointResponse>> convertStepsToPoints(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody StepConvertRequestDto request) {

        int converted = stepService.convertStepsToPoints(user.getUserId(), request.requestedPoints());
        User u = userRepository.findById(user.getUserId()).orElseThrow();

        int remaining = stepService.getRemainingConvertiblePoints(u);

        ConvertPointResponse response = new ConvertPointResponse(
                converted,
                u.getHallabong(),
                u.getMoodGrade(),
                remaining
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
}
