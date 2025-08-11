package com.goodda.jejuday.notification.dto;


        import com.goodda.jejuday.notification.entity.NotificationType;
        import io.swagger.v3.oas.annotations.media.Schema;
        import java.time.LocalDateTime;
        import lombok.Builder;
        import lombok.Getter;

@Getter
@Builder
@Schema(description = "알림 정보 DTO")
public class NotificationDto {

    @Schema(description = "알림 ID", example = "1")
    private Long id;

    @Schema(description = "알림 메시지", example = "새로운 댓글이 달렸습니다.")
    private String message;

    @Schema(description = "알림 타입")
    private NotificationType type;

    @Schema(description = "알림 생성 시간")
    private LocalDateTime createdAt;

    @Schema(description = "읽음 여부", example = "false")
    private boolean isRead;

    @Schema(description = "발신자 닉네임", example = "제주도민")
    private String nickname;

    @Schema(description = "알림 생성일 (포맷팅된 문자열)", example = "2025-01-15")
    private String formattedDate;

    @Schema(description = "알림 시간 (포맷팅된 문자열)", example = "14:30")
    private String formattedTime;

    // 생성 시 날짜 포맷팅
    public static NotificationDto of(com.goodda.jejuday.notification.entity.NotificationEntity entity) {
        return NotificationDto.builder()
                .id(entity.getId())
                .message(entity.getMessage())
                .type(entity.getType())
                .createdAt(entity.getCreatedAt())
                .isRead(entity.isRead())
                .nickname(entity.getUser().getNickname())
                .formattedDate(entity.getCreatedAt().toLocalDate().toString())
                .formattedTime(entity.getCreatedAt().toLocalTime().toString().substring(0, 5))
                .build();
    }
}