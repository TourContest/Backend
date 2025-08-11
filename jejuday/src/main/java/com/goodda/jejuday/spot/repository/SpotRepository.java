package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Spot;
import java.time.LocalDateTime;
import org.checkerframework.checker.units.qual.N;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface SpotRepository extends JpaRepository<Spot, Long> {
    @Query(value = """
    SELECT * FROM spot s
    WHERE s.is_deleted = false AND (
        6371 * acos(
            cos(radians(:lat)) *
            cos(radians(s.latitude)) *
            cos(radians(s.longitude) - radians(:lng)) +
            sin(radians(:lat)) *
            sin(radians(s.latitude))
        )
    ) <= :radius
""", nativeQuery = true)
    List<Spot> findWithinRadius(@Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng, @Param("radius") int radius);

    // 1) 최신순
    Page<Spot> findByTypeInOrderByCreatedAtDesc(
            Iterable<Spot.SpotType> types, Pageable pageable);

    // 2) 조회수순
    Page<Spot> findByTypeInOrderByViewCountDesc(
            Iterable<Spot.SpotType> types, Pageable pageable);

    // 3) 좋아요순
    Page<Spot> findByTypeInOrderByLikeCountDesc(
            Iterable<Spot.SpotType> types, Pageable pageable);

    // 사용자 정보를 함께 페치하여 N+1 문제 해결
    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.type IN :types ORDER BY s.createdAt DESC")
    Page<Spot> findByTypeInOrderByCreatedAtDescWithUser(@Param("types") Iterable<Spot.SpotType> types, Pageable pageable);

    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.type IN :types ORDER BY s.viewCount DESC")
    Page<Spot> findByTypeInOrderByViewCountDescWithUser(@Param("types") Iterable<Spot.SpotType> types, Pageable pageable);

    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.type IN :types ORDER BY s.likeCount DESC")
    Page<Spot> findByTypeInOrderByLikeCountDescWithUser(@Param("types") Iterable<Spot.SpotType> types, Pageable pageable);

    // 승격 프로세스용 - 삭제되지 않은 모든 스팟을 사용자 정보와 함께 조회
    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.isDeleted = false OR s.isDeleted IS NULL")
    List<Spot> findAllActiveSpotsWithUser();

    // 특정 타입의 스팟들을 사용자 정보와 함께 조회
    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.type = :type AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    List<Spot> findByTypeWithUser(@Param("type") Spot.SpotType type);

    // 특정 기간 이후 생성된 스팟들 조회
    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.createdAt >= :since AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    List<Spot> findActiveSpotsCreatedAfterWithUser(@Param("since") LocalDateTime since);

    // 특정 기간 이전 생성된 스팟들 조회 (정리용)
    @Query("SELECT s.id FROM Spot s WHERE s.createdAt < :before")
    List<Long> findSpotIdsCreatedBefore(@Param("before") LocalDateTime before);

    // 승격 대상 POST 타입 스팟들 (점수 계산 최적화를 위해 필요한 정보만 조회)
    @Query("""
        SELECT s FROM Spot s 
        JOIN FETCH s.user 
        WHERE s.type = 'POST' 
        AND (s.isDeleted = false OR s.isDeleted IS NULL)
        AND s.createdAt >= :cutoffDate
    """)
    List<Spot> findPromotionCandidatePosts(@Param("cutoffDate") LocalDateTime cutoffDate);

    // 승격 대상 SPOT 타입 스팟들
    @Query("""
        SELECT s FROM Spot s 
        JOIN FETCH s.user 
        WHERE s.type = 'SPOT' 
        AND (s.isDeleted = false OR s.isDeleted IS NULL)
        AND s.createdAt >= :cutoffDate
    """)
    List<Spot> findPromotionCandidateSpots(@Param("cutoffDate") LocalDateTime cutoffDate);

}