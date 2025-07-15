// SpotService.java
package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.dto.*;

import java.math.BigDecimal;
import java.util.List;

public interface SpotService {
    List<SpotResponse> getNearbySpots(BigDecimal lat, BigDecimal lng, int radiusKm);
    SpotDetailResponse getSpotDetail(Long id, User user);
    Long createSpot(SpotCreateRequest request, User user);
    void updateSpot(Long id, SpotUpdateRequest request, User user);
    void deleteSpot(Long id, User user);
    void likeSpot(Long id, User user);
    void unlikeSpot(Long id, User user);
    void bookmarkSpot(Long id, User user);
    void unbookmarkSpot(Long id, User user);
}
