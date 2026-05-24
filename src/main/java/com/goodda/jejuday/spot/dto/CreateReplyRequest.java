package com.goodda.jejuday.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateReplyRequest {
    @Schema(description = "댓글 내용", example = "이 장소 정말 좋아요!")
    private String text;
}