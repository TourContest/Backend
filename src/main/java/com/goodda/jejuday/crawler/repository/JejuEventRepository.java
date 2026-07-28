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

    /**
     * 배너 노출 대상 조회.
     * 비짓제주 API가 기간을 제공하지 않아 대부분 period가 null이며,
     * 기간이 있는 행사를 앞에, 기간 미상을 뒤에 배치한다.
     */
    @Query("""
           select e
           from JejuEvent e
           where e.bannerVisible = true
             and (e.periodStart is null or e.periodStart <= :date)
             and (e.periodEnd   is null or e.periodEnd   >= :date)
           order by case when e.periodEnd is null then 1 else 0 end asc,
                    e.periodEnd asc,
                    e.id desc
           """)
    List<JejuEvent> findActiveOn(@Param("date") LocalDate date, Pageable pageable);
}