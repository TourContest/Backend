package com.goodda.jejuday.spot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Data
@Getter
public class SpotUpdateRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private BigDecimal latitude;

    @NotNull
    private BigDecimal longitude;

    private Long themeId;   // null 이면 미선택
    private String tag1;    // null 이면 미선택
    private String tag2;
    private String tag3;

    @Schema(description = "상세 화면에서 그대로 유지할 기존 이미지 URL들(순서대로). 새 파일과 합쳐 최종 3장까지만 저장.")
    private List<String> keepImageUrls;
}