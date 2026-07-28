package com.goodda.jejuday.crawler.service;

import com.goodda.jejuday.crawler.dto.CommunityEventBannerDto;
import com.goodda.jejuday.crawler.entitiy.JejuEvent;
import com.goodda.jejuday.crawler.repository.JejuEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityEventBannerService {

    private final JejuEventRepository jejuEventRepository;
    private static final int BANNER_LIMIT = 10;

    public List<CommunityEventBannerDto> findBanners(LocalDate date) {
        return jejuEventRepository.findActiveOn(date, PageRequest.of(0, BANNER_LIMIT))
                .stream()
                .map(CommunityEventBannerDto::from)
                .toList();
    }
}
