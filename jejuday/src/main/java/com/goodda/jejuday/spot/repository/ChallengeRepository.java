package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Spot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ChallengeRepository extends JpaRepository<Spot, Long> {

    // 테마별 랜덤 N개 (placeholder: 추천 알고리즘 자리)
    @Query(value = """
        SELECT * FROM spot s
        WHERE s.type = 'CHALLENGE' AND s.is_deleted = 0 AND s.theme_id = :themeId
        ORDER BY RAND() LIMIT :limit
    """, nativeQuery = true)
    List<Spot> findRandomByTheme(@Param("themeId") Long themeId,
                                 @Param("limit") int limit);

    // 랜덤 N개 (placeholder: 추천 알고리즘 자리)
    @Query(value = """
        SELECT * FROM spot s
        WHERE s.type = 'CHALLENGE' AND s.is_deleted = 0
        ORDER BY RAND() LIMIT :limit
  """, nativeQuery = true)
    List<Spot> findRandom(@Param("limit") int limit);

    @Query(value = """
        SELECT * FROM spot s
        WHERE s.type = 'CHALLENGE' AND s.is_deleted = 0 AND s.theme_id = :themeId
        ORDER BY RAND() LIMIT :n
  """, nativeQuery = true)
    List<Spot> pickRandomByTheme(@Param("themeId") Long themeId, @Param("n") int n);

    // 1. Theme와 함께 조회하는 메서드 (LazyInitializationException 해결)
    @Query("""
    select s from Spot s
    left join fetch s.theme
    where s.id in :ids
    and s.type = 'CHALLENGE'
    and (s.isDeleted is null or s.isDeleted = false)
    """)
    List<Spot> findByIdInWithTheme(@Param("ids") List<Long> ids);

    @Query("""
    select s from Spot s
    left join fetch s.theme
    where s.theme.id = :themeId
    and s.type = :type
    and (s.isDeleted is null or s.isDeleted = false)
    order by s.createdAt desc
    """)
    List<Spot> findByThemeAndType(@Param("themeId") Long themeId,
                                  @Param("type") Spot.SpotType type,
                                  Pageable pageable);

    @Query("""
    select s from Spot s
    left join fetch s.theme
    where s.type = :type
    and (s.isDeleted is null or s.isDeleted = false)
    order by s.createdAt desc
    """)
    List<Spot> findByType(@Param("type") Spot.SpotType type, Pageable pageable);

//    // 진행중 목록 (무한스크롤: lastId보다 작은 id만 내려줌)
//    @Query("""
//        select s from Spot s
//        where s.type = com.goodda.jejuday.spot.entity.Spot$SpotType.CHALLENGE
//          and s.isDeleted = false
//          and s.startDate <= :today
//          and (s.endDate is null or s.endDate >= :today)
//          and (:lastId is null or s.id < :lastId)
//        order by s.id desc
//    """)
//    List<Spot> findOngoing(@Param("today") LocalDate today,
//                           @Param("lastId") Long lastId,
//                           Pageable pageable);
//
//    // 완료 목록
//    @Query("""
//        select s from Spot s
//        where s.type = com.goodda.jejuday.spot.entity.Spot$SpotType.CHALLENGE
//          and s.isDeleted = false
//          and s.endDate < :today
//          and (:lastId is null or s.id < :lastId)
//        order by s.id desc
//    """)
//    List<Spot> findCompleted(@Param("today") LocalDate today,
//                             @Param("lastId") Long lastId,
//                             Pageable pageable);
//
//    // 진행전 랜덤 1개 (MySQL/MariaDB 기준)
//    @Query(value = """
//        SELECT * FROM spot
//        WHERE type = 'CHALLENGE'
//          AND is_deleted = 0
//          AND start_date > :today
//        ORDER BY RAND()
//        LIMIT 1
//    """, nativeQuery = true)
//    Spot pickRandomUpcoming(@Param("today") LocalDate today);
}