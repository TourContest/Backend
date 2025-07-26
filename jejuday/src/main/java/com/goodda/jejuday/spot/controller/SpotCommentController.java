package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.spot.dto.ReplyDTO;
import com.goodda.jejuday.spot.service.SpotCommentService;
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
    public ResponseEntity<ReplyDTO> createComment(
            @PathVariable Long spotId,
            @RequestBody ReplyDTO dto) {
        dto.setContentId(spotId);
        dto.setDepth(0);
        ReplyDTO created = commentService.create(dto);
        return ResponseEntity.ok(created);
    }

    // 2. 대댓글 생성 (depth=parent.depth+1)
    @PostMapping("/{parentReplyId}/reply")
    public ResponseEntity<ReplyDTO> createReply(
            @PathVariable Long spotId,
            @PathVariable Long parentReplyId,
            @RequestBody ReplyDTO dto) {
        dto.setContentId(spotId);
        dto.setParentReplyId(parentReplyId);
        ReplyDTO created = commentService.create(dto);
        return ResponseEntity.ok(created);
    }

    // 3. 스팟의 모든 최상위 댓글 조회
    @GetMapping
    public ResponseEntity<List<ReplyDTO>> getComments(@PathVariable Long spotId) {
        return ResponseEntity.ok(commentService.findTopLevelBySpot(spotId));
    }

    // 4. 특정 댓글의 대댓글 조회
    @GetMapping("/{parentReplyId}/replies")
    public ResponseEntity<List<ReplyDTO>> getReplies(
            @PathVariable Long spotId,
            @PathVariable Long parentReplyId) {
        return ResponseEntity.ok(commentService.findReplies(parentReplyId));
    }

    // 5. 댓글/대댓글 수정
    @PutMapping("/{replyId}")
    public ResponseEntity<ReplyDTO> update(
            @PathVariable Long spotId,
            @PathVariable Long replyId,
            @RequestBody ReplyDTO dto) {
        dto.setId(replyId);
        return ResponseEntity.ok(commentService.update(dto));
    }

    // 6. 댓글/대댓글 삭제
    @DeleteMapping("/{replyId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long spotId,
            @PathVariable Long replyId) {
        commentService.delete(replyId);
        return ResponseEntity.noContent().build();
    }
}