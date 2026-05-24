package com.goodda.jejuday.attendance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttendanceResponse(
        String status,
        String message,
        Integer days,
        Integer baseHallabong,
        Integer bonusHallabong,
        Integer totalHallabong
) {
    public static AttendanceResponse alreadyChecked() {
        return new AttendanceResponse(
                "already",
                "이미 오늘 출석체크를 완료했어요!",
                null,
                null,
                null,
                null
        );
    }

    public static AttendanceResponse success(int days, int base, int bonus, int total) {
        return new AttendanceResponse(
                "success",
                "출석체크 완료! 🎉",
                days,
                base,
                bonus,
                total
        );
    }
}