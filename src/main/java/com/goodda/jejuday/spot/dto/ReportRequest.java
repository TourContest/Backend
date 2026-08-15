package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.Report;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportRequest {

    @Schema(description = "신고 사유", example = "SPAM")
    @NotNull(message = "신고 사유는 필수입니다.")
    private Report.ReportReason reason;

    @Schema(description = "상세 내용(선택)", example = "동일 게시글을 반복해서 도배하고 있습니다.")
    @Size(max = 500, message = "상세 내용은 500자를 넘을 수 없습니다.")
    private String description;
}
