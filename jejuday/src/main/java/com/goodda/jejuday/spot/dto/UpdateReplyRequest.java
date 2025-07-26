package com.goodda.jejuday.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateReplyRequest {
    @Schema(description = "수정할 댓글 내용", example = "내용을 이렇게 바꿨습니다.")
    private String text;
}