package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.SearchHistory;
import com.goodda.jejuday.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
    // 특정 사용자의 기록을 최신순으로 페이징 조회
    Page<SearchHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}