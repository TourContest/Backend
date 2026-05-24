package com.goodda.jejuday.spot.service;

import java.util.List;

public interface SearchHistoryService {
    /**
     * 사용자가 입력한 검색어를 기록합니다.
     * @param keyword 검색어
     */
    void recordSearch(String keyword);

    /**
     * 최근에 사용자가 검색한 키워드를 최신순으로 limit 개수만큼 조회합니다.
     * @param limit 조회할 최대 개수
     * @return 키워드 목록
     */
    List<String> getRecentSearchHistory(int limit);
}