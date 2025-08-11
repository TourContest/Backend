// SpotService.java
package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.dto.*;
import com.goodda.jejuday.spot.entity.Spot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;

public interface SpotService {
    List<SpotResponse> getNearbySpots(BigDecimal lat, BigDecimal lng, int radiusKm);
    SpotDetailResponse getSpotDetail(Long id);
    Long createSpot(SpotCreateRequest request);
    void updateSpot(Long id, SpotUpdateRequest request);
    void deleteSpot(Long id);
    void likeSpot(Long id);
    void unlikeSpot(Long id);
    void bookmarkSpot(Long id);
    void unbookmarkSpot(Long id);
    Spot getSpotById(Long spotId);

    Page<SpotResponse> getLatestSpots(Pageable pageable);
    Page<SpotResponse> getMostViewedSpots(Pageable pageable);
    Page<SpotResponse> getMostLikedSpots(Pageable pageable);
}
