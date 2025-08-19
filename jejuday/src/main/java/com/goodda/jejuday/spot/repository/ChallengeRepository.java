package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Spot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ChallengeRepository extends JpaRepository<Spot, Long> {

    // 진행중 목록 (무한스크롤: lastId보다 작은 id만 내려줌)
    @Query("""
        select s from Spot s
        where s.type = com.goodda.jejuday.spot.entity.Spot$SpotType.CHALLENGE
          and s.isDeleted = false
          and s.startDate <= :today
          and (s.endDate is null or s.endDate >= :today)
          and (:lastId is null or s.id < :lastId)
        order by s.id desc
    """)
    List<Spot> findOngoing(@Param("today") LocalDate today,
                           @Param("lastId") Long lastId,
                           Pageable pageable);

    // 완료 목록
    @Query("""
        select s from Spot s
        where s.type = com.goodda.jejuday.spot.entity.Spot$SpotType.CHALLENGE
          and s.isDeleted = false
          and s.endDate < :today
          and (:lastId is null or s.id < :lastId)
        order by s.id desc
    """)
    List<Spot> findCompleted(@Param("today") LocalDate today,
                             @Param("lastId") Long lastId,
                             Pageable pageable);

    // 진행전 랜덤 1개 (MySQL/MariaDB 기준)
    @Query(value = """
        SELECT * FROM spot
        WHERE type = 'CHALLENGE'
          AND is_deleted = 0
          AND start_date > :today
        ORDER BY RAND()
        LIMIT 1
    """, nativeQuery = true)
    Spot pickRandomUpcoming(@Param("today") LocalDate today);
}