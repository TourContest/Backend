package com.goodda.jejuday.spot.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReplyDTO {
    private Long id;
    private Long contentId;       // Spot ID
    private Integer depth;        // 0 = comment, >0 = reply depth
    private Long parentReplyId;   // null for top-level
    private Long memberId;
    private String memberNickname;
    private String text;
    private String relativeTime;  // e.g. "5분 전"
    private Boolean isDeleted;
    private LocalDateTime createdAt;
}