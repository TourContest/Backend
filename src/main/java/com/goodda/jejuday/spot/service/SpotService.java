// SpotService.java
package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.spot.dto.*;
import com.goodda.jejuday.spot.entity.Spot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

public interface SpotService {
    List<NearSpotResponse> getNearbySpots(BigDecimal lat, BigDecimal lng, int radiusKm);
    SpotDetailResponse getSpotDetail(Long id);

    Long createSpot(SpotCreateRequestDTO req, List<MultipartFile> images); // [신규-멀티파트용]
    void updateSpot(Long id, SpotUpdateRequest request, List<MultipartFile> images);
    void deleteSpot(Long id);

    void likeSpot(Long id);
    void unlikeSpot(Long id);
    void bookmarkSpot(Long id);
    void unbookmarkSpot(Long id);
    Spot getSpotById(Long spotId);

    Page<SpotResponse> getLatestSpots(Pageable pageable);
    Page<SpotResponse> getMostViewedSpots(Pageable pageable);
    Page<SpotResponse> getMostLikedSpots(Pageable pageable);

    // 마이페이지 관련 메서드
    Page<SpotResponse> getMyPosts(Pageable pageable, String sort);
    Page<ReplyDTO> getMyComments(Pageable pageable);
    Page<SpotResponse> getMyLikedSpots(Pageable pageable);

}
