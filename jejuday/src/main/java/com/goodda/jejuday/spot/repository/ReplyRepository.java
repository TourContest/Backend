package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Reply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReplyRepository extends JpaRepository<Reply, Long> {

    /** (페이징) 스팟의 최상위 댓글(depth=0) 목록, 생성일시 내림차순 */
    Page<Reply> findByContentIdAndDepth(
            Long contentId,
            int depth,
            Pageable pageable
    );

    /** (리스트) 스팟의 최상위 댓글(depth=0) 목록, 생성일시 내림차순 */
    List<Reply> findByContentIdAndDepthOrderByCreatedAtDesc(
            Long contentId,
            int depth
    );

    /** (페이징) 특정 댓글의 대댓글, 생성일시 오름차순 */
    Page<Reply> findByParentReplyId(
            Long parentReplyId,
            Pageable pageable
    );

    /** (리스트) 특정 댓글의 대댓글, 생성일시 오름차순 */
    List<Reply> findByParentReplyIdOrderByCreatedAtAsc(
            Long parentReplyId
    );
}
