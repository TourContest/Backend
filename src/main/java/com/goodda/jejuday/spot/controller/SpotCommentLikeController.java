// com.goodda.jejuday.spot.controller.SpotCommentLikeController.java
package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Reply;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Tag(name = "Spot Comment Like", description = "스팟 댓글 좋아요 등록/취소/조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/spots/{spotId}/comments/{replyId}/likes")
public class SpotCommentLikeController {

    private final LikeRepository likeRepository;
    private final ReplyRepository replyRepository;
    private final SpotRepository spotRepository;
    private final SecurityUtil securityUtil;

    @Operation(summary = "댓글 좋아요 등록", description = "댓글(또는 대댓글)에 좋아요를 등록합니다. 이미 눌렀다면 아무 동작도 하지 않습니다.")
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

    @Operation(summary = "댓글 좋아요 취소", description = "댓글(또는 대댓글)에 등록한 좋아요를 취소합니다.")
    @DeleteMapping
    public ResponseEntity<Void> unlikeReply(@PathVariable Long spotId, @PathVariable Long replyId) {
        var me = securityUtil.getAuthenticatedUser();
        likeRepository.findByUser_IdAndTargetIdAndTargetType(
                        me.getId(), replyId, Like.TargetType.REPLY
                )
                .ifPresent(likeRepository::delete);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "댓글 좋아요 개수 조회", description = "댓글(또는 대댓글)에 눌린 좋아요 총 개수를 조회합니다.")
    @GetMapping("/count")
    public ResponseEntity<Long> countReplyLikes(@PathVariable Long spotId, @PathVariable Long replyId) {
        long count = likeRepository.countByTargetIdAndTargetType(replyId, Like.TargetType.REPLY);
        return ResponseEntity.ok(count);
    }

    @Operation(summary = "내 댓글 좋아요 여부 조회", description = "로그인한 유저가 해당 댓글(또는 대댓글)에 좋아요를 눌렀는지 여부를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<Boolean> likedByMe(@PathVariable Long spotId, @PathVariable Long replyId) {
        var me = securityUtil.getAuthenticatedUser();
        boolean liked = likeRepository.existsByUser_IdAndTargetIdAndTargetType(
                me.getId(), replyId, Like.TargetType.REPLY
        );
        return ResponseEntity.ok(liked);
    }
}
