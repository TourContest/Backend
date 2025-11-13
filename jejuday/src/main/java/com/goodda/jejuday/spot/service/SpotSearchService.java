package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.spot.entity.Spot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SpotSearchService {
    /**
     * 지도 검색 (Trie → ID 목록 → DB 조회)
     * @param prefix 검색어 prefix
     * @return matching Spot 목록
     */
    List<Spot> searchMapSpotsByTrie(String prefix);

    /**
     * 커뮤니티 검색 (SQL LIKE + 페이징)
     * @param query 검색어
     * @param pageable 페이징 정보
     * @return 페이징된 Spot 목록
     */
    Page<Spot> searchCommunitySpotsBySql(String query, Pageable pageable);
}