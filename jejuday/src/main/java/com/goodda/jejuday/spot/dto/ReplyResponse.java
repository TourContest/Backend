package com.goodda.jejuday.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyResponse {

    @Schema(description = "댓글 고유 ID", example = "123")
    private Long id;

    private Long contentId;

    @Schema(description = "부모 댓글 ID (대댓글일 때만)", example = "42", nullable = true)
    private Long parentReplyId;

    @Schema(description = "댓글 깊이 (0=댓글, 1=대댓글)", example = "0")
    private int depth;

    @Schema(description = "댓글 내용", example = "이 장소 정말 좋아요!")
    private String text;

    @Schema(description = "작성자 닉네임", example = "pray")
    private String nickname;

    @Schema(description = "소프트 삭제 여부", example = "false")
    private Boolean isDeleted;

    @Schema(description = "작성 시각", example = "2025-07-18T18:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정 시각 (수정 안 했으면 작성 시각과 같거나 null)", example = "2025-07-18T18:10:00", nullable = true)
    private LocalDateTime updatedAt;

    // 필요하면 상대시간 필드도 추가 가능
    @Schema(description = "예: \"5분 전\"", example = "5분 전")
    private String relativeTime;
}