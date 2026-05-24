package com.goodda.jejuday.attendance.controller;

import com.goodda.jejuday.attendance.dto.AttendanceResult;
import com.goodda.jejuday.attendance.dto.AttendanceResponse;
import com.goodda.jejuday.attendance.service.AttendanceService;
import com.goodda.jejuday.auth.security.CustomUserDetails;
import com.goodda.jejuday.notification.service.AttendanceReminderScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "출석 API", description = "사용자의 일일 출석체크 관련 기능")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceReminderScheduler attendanceReminderScheduler;

    @PostMapping("/check")
    @Operation(
            summary = "출석체크 요청",
            description = "오늘 출석 여부를 확인하고, 출석처리를 진행합니다.\n\n" +
                    "- 연속 출석일 수에 따라 한라봉 포인트가 증가합니다.\n" +
                    "- 7일마다 보너스 한라봉이 추가 지급됩니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<AttendanceResponse> checkAttendance(
            @AuthenticationPrincipal CustomUserDetails user) {

        AttendanceResult result = attendanceService.checkAttendance(user.getUserId());

        if (result.alreadyChecked()) {
            return ResponseEntity.ok(AttendanceResponse.alreadyChecked());
        }

        // 출석 체크 성공 시 캐시 업데이트
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            attendanceReminderScheduler.markAttendanceChecked(user.getUserId(), today);
            log.debug("출석 체크 캐시 업데이트 완료: 사용자={}", user.getUserId());
        } catch (Exception e) {
            log.warn("출석 체크 캐시 업데이트 실패: 사용자={}, 에러={}", user.getUserId(), e.getMessage());
            // 캐시 업데이트 실패는 출석 체크 자체에는 영향을 주지 않음
        }

        return ResponseEntity.ok(AttendanceResponse.success(
                result.consecutiveDays(),
                result.baseHallabong(),
                result.bonusHallabong(),
                result.totalHallabong()
        ));
    }
}
