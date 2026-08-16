package com.goodda.jejuday.attendance.dto;

public record AttendanceStatusResponse(
        boolean checkedToday,
        int consecutiveDays
) {
    public static AttendanceStatusResponse of(boolean checkedToday, int consecutiveDays) {
        return new AttendanceStatusResponse(checkedToday, consecutiveDays);
    }
}
