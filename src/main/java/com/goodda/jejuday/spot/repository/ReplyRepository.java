package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Reply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReplyRepository extends JpaRepository<Reply, Long> {

    /** (페이징) 스팟의 최상위 댓글(depth=0) 목록, 생성일시 내림차순 — 차단한 유저의 댓글은 제외 */
    @Query("SELECT r FROM Reply r WHERE r.contentId = :contentId AND r.depth = :depth AND r.user.id NOT IN :blockedUserIds")
    Page<Reply> findByContentIdAndDepth(
            @Param("contentId") Long contentId,
            @Param("depth") int depth,
            @Param("blockedUserIds") List<Long> blockedUserIds,
            Pageable pageable
    );

    /** (리스트) 스팟의 최상위 댓글(depth=0) 목록, 생성일시 내림차순 */
    List<Reply> findByContentIdAndDepthOrderByCreatedAtDesc(
            Long contentId,
            int depth
    );

    /** (페이징) 특정 댓글의 대댓글, 생성일시 오름차순 — 차단한 유저의 댓글은 제외 */
    @Query("SELECT r FROM Reply r WHERE r.parentReply.id = :parentReplyId AND r.user.id NOT IN :blockedUserIds")
    Page<Reply> findByParentReplyId(
            @Param("parentReplyId") Long parentReplyId,
            @Param("blockedUserIds") List<Long> blockedUserIds,
            Pageable pageable
    );

    /** (리스트) 특정 댓글의 대댓글, 생성일시 오름차순 */
    List<Reply> findByParentReplyIdOrderByCreatedAtAsc(
            Long parentReplyId
    );

    /** 사용자가 작성한 댓글 조회 (삭제되지 않은 것만) - 페이징 지원 */
    @Query("SELECT r FROM Reply r JOIN FETCH r.user WHERE r.user.id = :userId AND (r.isDeleted = false OR r.isDeleted IS NULL) ORDER BY r.createdAt DESC")
    Page<Reply> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    /** 스팟의 최상위 댓글(depth=0) 개수만 조회 — 엔티티 전량 로딩 없이 카운트 */
    int countByContentIdAndDepth(Long contentId, int depth);

    /**
     * engagement(승격 점수)용 — 삭제되지 않은 최상위 댓글만 센다. is_deleted 는 NULL 일 수 있어 함께 허용.
     * 화면 표시용 댓글 수(countByContentIdAndDepth)와 의도적으로 분리: 점수는 살아 있는 반응만 반영한다.
     */
    @Query("""
        SELECT COUNT(r) FROM Reply r
        WHERE r.contentId = :contentId AND r.depth = :depth
          AND (r.isDeleted = false OR r.isDeleted IS NULL)
    """)
    int countActiveByContentIdAndDepth(@Param("contentId") Long contentId, @Param("depth") int depth);

    /** 보정 배치용 — countActiveByContentIdAndDepth 의 GROUP BY 일괄 버전 (두 경로의 정의를 일치시킨다) */
    @Query("""
        SELECT r.contentId, COUNT(r)
        FROM Reply r
        WHERE r.contentId IN :contentIds AND r.depth = :depth
          AND (r.isDeleted = false OR r.isDeleted IS NULL)
        GROUP BY r.contentId
    """)
    List<Object[]> countActiveGroupByContentIds(@Param("contentIds") List<Long> contentIds, @Param("depth") int depth);

    /** 보정 배치용 — 여러 스팟의 최상위 댓글 개수를 GROUP BY로 일괄 조회 */
    @Query("""
        SELECT r.contentId, COUNT(r)
        FROM Reply r
        WHERE r.contentId IN :contentIds AND r.depth = :depth
        GROUP BY r.contentId
    """)
    List<Object[]> countGroupByContentIds(@Param("contentIds") List<Long> contentIds, @Param("depth") int depth);
}
