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

    /** TourAPI 위치 캐시 등으로 런타임에 추가된 장소를 즉시 검색 가능하게 만든다. */
    void indexSpot(Spot spot);
}
