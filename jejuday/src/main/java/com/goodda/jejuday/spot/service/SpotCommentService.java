package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.spot.dto.CreateReplyRequest;
import com.goodda.jejuday.spot.dto.ReplyRequest;
import com.goodda.jejuday.spot.dto.ReplyResponse;
import com.goodda.jejuday.spot.dto.UpdateReplyRequest;

import java.util.List;

public interface SpotCommentService {

    /** 최상위 댓글 생성 */
    ReplyResponse createComment(Long spotId, ReplyRequest request);

    /** 대댓글 생성 */
    ReplyResponse createReply(Long spotId, Long parentReplyId, ReplyRequest request);

    /** 스팟에 달린 최상위 댓글 조회 */
    List<ReplyResponse> findTopLevelBySpot(Long spotId);

    /** 특정 댓글의 대댓글 조회 */
    List<ReplyResponse> findReplies(Long parentReplyId);

    /** 댓글 수정 */
    ReplyResponse update(Long replyId, String text);

    /** 댓글 삭제 (204 No Content) */
    void delete(Long replyId);
}