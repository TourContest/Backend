package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.SpotViewLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpotViewLogRepository extends JpaRepository<SpotViewLog, Long> {

    // 혼잡도 계산용 - 최근 기간 내 스팟별 조회수 집계
    @Query("SELECT v.spot.id, COUNT(v) FROM SpotViewLog v WHERE v.viewedAt >= :since GROUP BY v.spot.id")
    List<Object[]> countRecentViewsGroupedBySpot(@Param("since") LocalDateTime since);
}