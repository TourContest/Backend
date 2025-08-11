package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Reply;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * 여러 스팟의 최상위 댓글 수를 배치로 조회 (N+1 문제 해결)
     */
    @Query("""
        SELECT r.contentId, COUNT(r) 
        FROM Reply r 
        WHERE r.contentId IN :contentIds AND r.depth = 0 
        GROUP BY r.contentId
    """)
    List<Object[]> countTopLevelRepliesByContentIds(@Param("contentIds") List<Long> contentIds);

    /**
     * 스팟 ID 목록에 대한 댓글 수를 Map으로 반환하는 기본 메서드
     */
    default Map<Long, Long> getReplyCountsForSpots(List<Long> spotIds) {
        List<Object[]> results = countTopLevelRepliesByContentIds(spotIds);
        return results.stream()
                .collect(java.util.stream.Collectors.toMap(
                        result -> (Long) result[0],
                        result -> (Long) result[1]
                ));
    }

    /**
     * 특정 스팟의 모든 댓글 수 (대댓글 포함)
     */
    @Query("SELECT COUNT(r) FROM Reply r WHERE r.contentId = :contentId")
    int countAllRepliesByContentId(@Param("contentId") Long contentId);

    /**
     * 여러 스팟의 모든 댓글 수를 배치로 조회
     */
    @Query("""
        SELECT r.contentId, COUNT(r) 
        FROM Reply r 
        WHERE r.contentId IN :contentIds 
        GROUP BY r.contentId
    """)
    List<Object[]> countAllRepliesByContentIds(@Param("contentIds") List<Long> contentIds);

    /**
     * 스팟 ID 목록에 대한 전체 댓글 수를 Map으로 반환
     */
    default Map<Long, Long> getAllReplyCountsForSpots(List<Long> spotIds) {
        List<Object[]> results = countAllRepliesByContentIds(spotIds);
        return results.stream()
                .collect(java.util.stream.Collectors.toMap(
                        result -> (Long) result[0],
                        result -> (Long) result[1]
                ));
    }

    /**
     * 특정 기간 이후의 댓글들을 조회 (활성도 측정용)
     */
    @Query("""
        SELECT r.contentId, COUNT(r) 
        FROM Reply r 
        WHERE r.contentId IN :contentIds 
        AND r.createdAt >= :sinceDate 
        GROUP BY r.contentId
    """)
    List<Object[]> countRecentRepliesByContentIds(@Param("contentIds") List<Long> contentIds,
                                                  @Param("sinceDate") java.time.LocalDateTime sinceDate);
}
