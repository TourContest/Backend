package com.goodda.jejuday.attendance.dto;

public record AttendanceResult(
        boolean success,
        boolean alreadyChecked,
        int consecutiveDays,
        int baseHallabong,
        int bonusHallabong,
        int totalHallabong
) {
    public static AttendanceResult ofAlreadyChecked() {
        return new AttendanceResult(false, true, 0, 0, 0, 0);
    }

    public static AttendanceResult ofSuccess(int days, int base, int bonus) {
        return new AttendanceResult(true, false, days, base, bonus, base + bonus);
    }
}