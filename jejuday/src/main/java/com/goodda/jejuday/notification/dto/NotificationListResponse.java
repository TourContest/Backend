package com.goodda.jejuday.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "알림 목록 응답 (페이징 포함)")
public class NotificationListResponse {

    @Schema(description = "알림 목록")
    private List<NotificationDto> notifications;

    @Schema(description = "총 알림 수", example = "150")
    private long totalCount;

    @Schema(description = "읽지 않은 알림 수", example = "5")
    private long unreadCount;

    @Schema(description = "현재 페이지", example = "0")
    private int currentPage;

    @Schema(description = "총 페이지 수", example = "15")
    private int totalPages;

    @Schema(description = "페이지당 알림 수", example = "10")
    private int pageSize;

    @Schema(description = "다음 페이지 존재 여부", example = "true")
    private boolean hasNext;

    @Schema(description = "이전 페이지 존재 여부", example = "false")
    private boolean hasPrevious;
}