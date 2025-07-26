package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.spot.dto.ReplyDTO;

import java.util.List;

public interface SpotCommentService {
    ReplyDTO create(ReplyDTO dto);
    List<ReplyDTO> findTopLevelBySpot(Long spotId);
    List<ReplyDTO> findReplies(Long parentReplyId);
    ReplyDTO update(ReplyDTO dto);
    void delete(Long replyId);
}