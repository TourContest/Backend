package com.goodda.jejuday.crawler.repository;

import com.goodda.jejuday.crawler.entitiy.JejuEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import java.time.LocalDate;
import java.util.List;


public interface JejuEventRepository extends JpaRepository<JejuEvent, Long> {
    Optional<JejuEvent> findByContentsId(String contentsId);

    /** 기준일(date)에 진행 중인 이벤트만 조회 (start <= date <= end, null 허용) */
    @Query("""
           select e
           from JejuEvent e
           where (:date is null
                  or ( (e.periodStart is null or e.periodStart <= :date)
                    and (e.periodEnd   is null or e.periodEnd   >= :date) ))
           order by coalesce(e.periodStart, e.periodEnd) asc, e.id desc
           """)
    List<JejuEvent> findActiveOn(@Param("date") LocalDate date);
}