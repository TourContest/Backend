package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.entity.Spot.SpotType;

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

    // 트라이 초기화용: SPOT + CHALLENGE
    List<Spot> findAllByTypeIn(List<SpotType> types);

    // 커뮤니티 검색: 이름 포함 + 타입 필터링
    Page<Spot> findByNameContainingIgnoreCaseAndTypeIn(String name, List<SpotType> types, Pageable pageable);
}