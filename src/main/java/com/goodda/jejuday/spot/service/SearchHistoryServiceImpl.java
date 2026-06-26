package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.spot.entity.SearchHistory;
import com.goodda.jejuday.spot.repository.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchHistoryServiceImpl implements SearchHistoryService {
    private final SearchHistoryRepository historyRepository;
    private final SecurityUtil securityUtil;

    @Override
    public void recordSearch(String keyword) {
        User me = securityUtil.getAuthenticatedUser();
        SearchHistory h = SearchHistory.builder()
                .user(me)
                .keyword(keyword)
                .build();
        historyRepository.save(h);
    }

    @Override
    public List<String> getRecentSearchHistory(int limit) {
        User me = securityUtil.getAuthenticatedUser();
        Pageable p = PageRequest.of(0, limit, Sort.by("createdAt").descending());
        return historyRepository.findByUserOrderByCreatedAtDesc(me, p)
                .stream()
                .map(SearchHistory::getKeyword)
                .collect(Collectors.toList());
    }
}
