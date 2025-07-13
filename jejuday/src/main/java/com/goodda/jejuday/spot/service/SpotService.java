package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.spot.dto.SpotCreateRequest;
import com.goodda.jejuday.spot.dto.SpotDetailResponse;
import com.goodda.jejuday.spot.dto.SpotResponse;
import com.goodda.jejuday.spot.dto.SpotUpdateRequest;

import java.math.BigDecimal;
import java.util.List;

public interface SpotService {
    // TODO : 싹다 수정 userId가 올개 아니라 User 가 와서 의존성 주입 받게 해야 하는거 아닌가?
    List<SpotResponse> getNearbySpots(BigDecimal lat, BigDecimal lng, int radiusKm);    SpotDetailResponse getSpotDetail(Long id, Long userId);
    Long createSpot(SpotCreateRequest request, Long userId);
    void updateSpot(Long id, SpotUpdateRequest request, Long userId);
    void deleteSpot(Long id, Long userId);
    void likeSpot(Long id, Long userId);
    void unlikeSpot(Long id, Long userId);
    void bookmarkSpot(Long id, Long userId);
    void unbookmarkSpot(Long id, Long userId);
}