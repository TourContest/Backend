package com.goodda.jejuday.attendance.dto;

public record AttendanceResult(
        boolean alreadyChecked,
        int consecutiveDays,
        int baseHallabong,
        int bonusHallabong,
        int totalHallabong
) {
    public static AttendanceResult ofSuccess(int consecutiveDays, int base, int bonus, int total) {
        return new AttendanceResult(false, consecutiveDays, base, bonus, total);
    }

    public static AttendanceResult ofAlreadyChecked() {
        return new AttendanceResult(true, 0, 0, 0, 0);
    }
}
