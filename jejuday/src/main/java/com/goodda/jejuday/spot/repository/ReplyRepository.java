package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Reply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReplyRepository extends JpaRepository<Reply, Long> {
    /**
     * 스팟의 최상위 댓글(depth=0) 목록을 생성일시 내림차순으로 조회
     */
    List<Reply> findByContentIdAndDepthOrderByCreatedAtDesc(Long contentId, Integer depth);

    /**
     * 특정 댓글의 대댓글 목록을 생성일시 오름차순으로 조회
     */
    List<Reply> findByParentReplyIdOrderByCreatedAtAsc(Long parentReplyId);
}
