package com.goodda.jejuday.attendance.util;

public class HallabongConstants {
    // 기존 상수
    public static final String TYPE = "HALLABONG";
    public static final int STEP_CONVERT_UNIT = 1000; // 1000걸음 = 한라봉 1개

    // 출석 관련 상수
    public static final int ATTENDANCE_BASE_HALLABONG = 100;
    public static final int ATTENDANCE_DAILY_INCREMENT = 20;
    public static final int ATTENDANCE_MAX_HALLABONG = 220;
    public static final int ATTENDANCE_BONUS_CYCLE = 7;
    public static final int ATTENDANCE_BONUS_AMOUNT = 500;
    public static final String ATTENDANCE_BONUS_TYPE = "attendance_7days";

    private HallabongConstants() {
        // 유틸리티 클래스 인스턴스화 방지
    }
}