package com.goodda.jejuday.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "알림 통계 정보")
public class NotificationStatsDto {

    @Schema(description = "총 알림 수", example = "150")
    private long totalCount;

    @Schema(description = "읽지 않은 알림 수", example = "5")
    private long unreadCount;

    @Schema(description = "읽은 알림 수", example = "145")
    private long readCount;

    @Schema(description = "최근 7일 알림 수", example = "23")
    private long recentCount;

    @Schema(description = "읽음 비율 (퍼센트)", example = "96.7")
    private double readPercentage;

    public static NotificationStatsDto of(long total, long unread) {
        long read = total - unread;
        double readPercentage = total > 0 ? (double) read / total * 100 : 0.0;

        return NotificationStatsDto.builder()
                .totalCount(total)
                .unreadCount(unread)
                .readCount(read)
                .readPercentage(Math.round(readPercentage * 10) / 10.0) // 소수점 1자리
                .build();
    }
}
