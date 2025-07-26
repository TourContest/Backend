package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.spot.dto.*;
import com.goodda.jejuday.spot.service.SpotCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spots/{spotId}/comments")
@RequiredArgsConstructor
public class SpotCommentController {

    private final SpotCommentService commentService;

    // 1. 댓글 생성 (depth=0)
    @PostMapping
    public ResponseEntity<ReplyResponse> createComment(
            @PathVariable Long spotId,
            @Valid @RequestBody ReplyRequest req
    ) {
        return ResponseEntity.ok(commentService.createComment(spotId, req));
    }


    // 2. 대댓글 생성 (depth=parent.depth+1)
    @PostMapping("/{parentReplyId}/replies")
    public ResponseEntity<ReplyResponse> createReply(
            @PathVariable Long spotId,
            @PathVariable Long parentReplyId,
            @Valid @RequestBody ReplyRequest req
    ) {
        return ResponseEntity.ok(commentService.createReply(spotId, parentReplyId, req));
    }

    // 3. 스팟의 모든 최상위 댓글 조회
    @GetMapping
    public ResponseEntity<List<ReplyResponse>> getComments(@PathVariable Long spotId) {
        return ResponseEntity.ok(commentService.findTopLevelBySpot(spotId));
    }

    // 4. 특정 댓글의 대댓글 조회
    @GetMapping("/{parentReplyId}/replies")
    public ResponseEntity<List<ReplyResponse>> getReplies(@PathVariable Long parentReplyId) {
        {
            return ResponseEntity.ok(commentService.findReplies(parentReplyId));
        }
    }

    // 5. 댓글/대댓글 수정
    @PutMapping("/{replyId}")
    public ResponseEntity<ReplyResponse> update(
            @PathVariable Long spotId,
            @PathVariable Long replyId,
            @Valid @RequestBody UpdateReplyRequest req
    ) {
        return ResponseEntity.ok(commentService.update(replyId, req.getText()));
    }


    // 6. 댓글/대댓글 삭제
    @DeleteMapping("/{replyId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long spotId,
            @PathVariable Long replyId
    ) {
        commentService.delete(replyId);
        return ResponseEntity.noContent().build();
    }
}