package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.spot.dto.ReportRequest;
import com.goodda.jejuday.spot.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Spot Report", description = "게시글/댓글 신고 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/spots")
public class SpotReportController {

    private final ReportService reportService;

    @Operation(summary = "게시글 신고", description = "커뮤니티 게시글(스팟)을 신고합니다. 본인 게시글이거나 이미 신고한 경우 실패합니다.")
    @PostMapping("/{spotId}/report")
    public ResponseEntity<Void> reportSpot(
            @PathVariable Long spotId,
            @Valid @RequestBody ReportRequest req
    ) {
        reportService.reportSpot(spotId, req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "댓글 신고", description = "게시글에 달린 댓글(또는 대댓글)을 신고합니다. 본인 댓글이거나 이미 신고한 경우 실패합니다.")
    @PostMapping("/{spotId}/comments/{replyId}/report")
    public ResponseEntity<Void> reportReply(
            @PathVariable Long spotId,
            @PathVariable Long replyId,
            @Valid @RequestBody ReportRequest req
    ) {
        reportService.reportReply(spotId, replyId, req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
