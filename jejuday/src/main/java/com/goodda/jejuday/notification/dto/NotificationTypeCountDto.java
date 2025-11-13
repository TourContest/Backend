package com.goodda.jejuday.notification.dto;

import com.goodda.jejuday.notification.entity.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "알림 타입별 개수 정보")
public class NotificationTypeCountDto {

    @Schema(description = "알림 타입")
    private NotificationType type;

    @Schema(description = "해당 타입의 알림 개수", example = "12")
    private long count;

    @Schema(description = "해당 타입의 읽지 않은 알림 개수", example = "3")
    private long unreadCount;

    @Schema(description = "타입 설명", example = "댓글 알림")
    private String typeDescription;

    public static NotificationTypeCountDto of(NotificationType type, long count, long unreadCount) {
        return NotificationTypeCountDto.builder()
                .type(type)
                .count(count)
                .unreadCount(unreadCount)
                .typeDescription(getTypeDescription(type))
                .build();
    }

    private static String getTypeDescription(NotificationType type) {
        return switch (type) {
            case REPLY -> "댓글 알림";
            case CHALLENGE -> "챌린지 알림";
            case STEP -> "걸음수 알림";
            case COMMENTS -> "대댓글 알림";
            case POPULARITY -> "인기글 승격 알림";
            case LIKE -> "좋아요 알림";
            case ATTENDANCE -> "출석 리마인더";
        };
    }
}
