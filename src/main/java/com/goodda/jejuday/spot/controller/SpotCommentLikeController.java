// com.goodda.jejuday.spot.controller.SpotCommentLikeController.java
package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Reply;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/spots/{spotId}/comments/{replyId}/likes")
public class SpotCommentLikeController {

    private final LikeRepository likeRepository;
    private final ReplyRepository replyRepository;
    private final SpotRepository spotRepository;
    private final SecurityUtil securityUtil;

    // 댓글 좋아요
    @PostMapping
    public ResponseEntity<Void> likeReply(@PathVariable Long spotId, @PathVariable Long replyId) {
        var me = securityUtil.getAuthenticatedUser();

        Reply reply = replyRepository.findById(replyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply not found"));

        // 경로의 spotId와 댓글의 contentId 일치 검증
        if (!spotId.equals(reply.getContentId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply does not belong to this spot");
        }

        // 이미 좋아요 눌렀는지 확인
        boolean exists = likeRepository.existsByUser_IdAndTargetIdAndTargetType(
                me.getId(), replyId, Like.TargetType.REPLY
        );
        if (!exists) {
            // Like.spot 이 not-null 이므로 Spot도 세팅
            Spot spot = spotRepository.findById(spotId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spot not found"));

            Like like = new Like();
            like.setUser(me);
            like.setSpot(spot);
            like.setTargetType(Like.TargetType.REPLY);
            like.setTargetId(replyId);
            likeRepository.save(like);
        }
        return ResponseEntity.noContent().build();
    }

    // 댓글 좋아요 취소
    @DeleteMapping
    public ResponseEntity<Void> unlikeReply(@PathVariable Long spotId, @PathVariable Long replyId) {
        var me = securityUtil.getAuthenticatedUser();
        likeRepository.findByUser_IdAndTargetIdAndTargetType(
                        me.getId(), replyId, Like.TargetType.REPLY
                )
                .ifPresent(likeRepository::delete);
        return ResponseEntity.noContent().build();
    }

    // 댓글 좋아요 개수
    @GetMapping("/count")
    public ResponseEntity<Long> countReplyLikes(@PathVariable Long spotId, @PathVariable Long replyId) {
        long count = likeRepository.countByTargetIdAndTargetType(replyId, Like.TargetType.REPLY);
        return ResponseEntity.ok(count);
    }

    // 내가 댓글 좋아요 눌렀는지
    @GetMapping("/me")
    public ResponseEntity<Boolean> likedByMe(@PathVariable Long spotId, @PathVariable Long replyId) {
        var me = securityUtil.getAuthenticatedUser();
        boolean liked = likeRepository.existsByUser_IdAndTargetIdAndTargetType(
                me.getId(), replyId, Like.TargetType.REPLY
        );
        return ResponseEntity.ok(liked);
    }
}
