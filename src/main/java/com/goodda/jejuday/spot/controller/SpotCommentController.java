package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.spot.dto.*;
import com.goodda.jejuday.spot.service.SpotCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Spot Comment", description = "스팟 댓글/대댓글 CRUD API")
@RestController
@RequestMapping("/api/spots/{spotId}/comments")
@RequiredArgsConstructor
public class SpotCommentController {

    private final SpotCommentService commentService;

    @Operation(summary = "최상위 댓글 목록 조회", description = "스팟의 최상위(depth=0) 댓글을 페이징 조회합니다.")
    @GetMapping
    public ResponseEntity<ReplyPageResponse> getComments(
            @PathVariable Long spotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        return ResponseEntity.ok(commentService.findTopLevelBySpot(spotId, page, size));
    }


    @Operation(summary = "답글 목록 조회", description = "특정 댓글(parentReplyId)에 달린 답글을 페이징 조회합니다.")
    @GetMapping("/{parentReplyId}/replies")
    public ResponseEntity<ReplyPageResponse> getReplies(
            @PathVariable Long parentReplyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(commentService.findReplies(parentReplyId, page, size));
    }

    @Operation(summary = "댓글 생성", description = "스팟에 최상위 댓글(depth=0)을 작성합니다.")
    @PostMapping
    public ReplyResponse createComment(
            @PathVariable Long spotId,
            @Valid @RequestBody ReplyRequest req
    ) {
        return commentService.createComment(spotId, req);
    }


    @Operation(summary = "대댓글 생성", description = "특정 댓글(parentReplyId)에 대한 답글을 작성합니다. depth는 parent.depth+1로 저장됩니다.")
    @PostMapping("/{parentReplyId}/replies")
    public ReplyResponse createReply(
            @PathVariable Long spotId,
            @PathVariable Long parentReplyId,
            @Valid @RequestBody ReplyRequest req
    ) {
        return commentService.createReply(spotId, parentReplyId, req);
    }

    // 3. 스팟의 모든 최상위 댓글 조회
//    @GetMapping
//    public ResponseEntity<List<ReplyResponse>> getComments(@PathVariable Long spotId) {
//        return ResponseEntity.ok(commentService.findTopLevelBySpot(spotId));
//    }

    // 4. 특정 댓글의 대댓글 조회
//    @GetMapping("/{parentReplyId}/replies")
//    public ResponseEntity<List<ReplyResponse>> getReplies(@PathVariable Long parentReplyId) {
//        {
//            return ResponseEntity.ok(commentService.findReplies(parentReplyId));
//        }
//    }

    @Operation(summary = "댓글/대댓글 수정", description = "작성한 댓글 또는 대댓글의 내용을 수정합니다.")
    @PutMapping("/{replyId}")
    public ResponseEntity<ReplyResponse> update(
            @PathVariable Long spotId,
            @PathVariable Long replyId,
            @Valid @RequestBody UpdateReplyRequest req
    ) {
        return ResponseEntity.ok(commentService.update(replyId, req.getText()));
    }


    @Operation(summary = "댓글/대댓글 삭제", description = "작성한 댓글 또는 대댓글을 삭제합니다.")
    @DeleteMapping("/{replyId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long spotId,
            @PathVariable Long replyId
    ) {
        commentService.delete(replyId);
        return ResponseEntity.noContent().build();
    }
}