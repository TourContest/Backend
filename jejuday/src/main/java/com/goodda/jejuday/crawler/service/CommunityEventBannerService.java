package com.goodda.jejuday.crawler.service;

import com.goodda.jejuday.crawler.dto.CommunityEventBannerDto;
import com.goodda.jejuday.crawler.entitiy.JejuEvent;
import com.goodda.jejuday.crawler.repository.JejuEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityEventBannerService {

    private final JejuEventRepository jejuEventRepository;

    public List<CommunityEventBannerDto> findBanners(LocalDate date) {
        List<JejuEvent> list = jejuEventRepository.findActiveOn(date);
        return list.stream().map(CommunityEventBannerDto::from).toList();
    }
}
