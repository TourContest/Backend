package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.dto.SpotCreateRequest;
import com.goodda.jejuday.spot.dto.SpotDetailResponse;
import com.goodda.jejuday.spot.dto.SpotResponse;
import com.goodda.jejuday.spot.dto.SpotUpdateRequest;
import com.goodda.jejuday.spot.service.SpotService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/spots")
@RequiredArgsConstructor
public class SpotController {
    private final SpotService spotService;


    // 1. 바텀네비 홈화면에서 위치 마커 띄우는 3안
    // 1)
    @GetMapping("/nearby")          // 홈화면에서 뛰울 위치 기반 위치 마커 read
    public List<SpotResponse> getNearbySpots(@RequestParam BigDecimal lat,
                                             @RequestParam BigDecimal lng,
                                             @RequestParam(defaultValue = "5") int radiusKm) {
        return spotService.getNearbySpots(lat, lng, radiusKm);
    }

    // 유저가 결정, Where 절에 넣어서 필터링, 근방 몇 km 까지?

    //


    // 위치 마커 클릭 시 상세 정보 보여주기
    @GetMapping("/{id}")
    public SpotDetailResponse getSpotDetail(@PathVariable Long id,
                                            @AuthenticationPrincipal User user) {
        return spotService.getSpotDetail(id, user);
    }
    
    // 사용자가 커뮤니티(바텀 네비게이션)를 통해서 spot 장소 등록
    @PostMapping
    public Long createSpot(@RequestBody SpotCreateRequest request,
                           @AuthenticationPrincipal User user) {
        return spotService.createSpot(request, user);
    }
    
    // TODO : 관리자 페이지에서 Spot 장소 등록, Spot 장소 등급업, Spot 장소 삭제

    // TODO 위치 마커 수정할때 전에 등록했던 정보를 가지고 오는 Controller 추가 필요
    // 사용자 위치 마커 수정 ( 위치가 잘못 되었을 때, or 내용 수정을 원할때 )
    @PutMapping("/{id}")
    public void updateSpot(@PathVariable Long id,
                           @RequestBody SpotUpdateRequest request,
                           @AuthenticationPrincipal User user) {
        spotService.updateSpot(id, request, user);
    }

    // 커뮤니티에 등록된 Spot 장소 삭제,
    // TODO : Default 로 지금은 모든 위치 마커 삭제로 놔두었지만, 등급업 된 Spot 장소의 경우에 삭제를 허용 할지 말지 추가 의논 필요.
    @DeleteMapping("/{id}")
    public void deleteSpot(@PathVariable Long id,
                           @AuthenticationPrincipal User user) {
        spotService.deleteSpot(id, user);
    }

    // Spot 장소에 대한 좋아요.
    @PostMapping("/{id}/like")
    public void likeSpot(@PathVariable Long id,
                         @AuthenticationPrincipal User user) {
        spotService.likeSpot(id, user);
    }

    // 한번더 누르면 취소됨.
    // TODO :: 지금은 이렇게 간단하게 해놓고 나중에 Redis 통해서 조회수 같은 것들 Lazy Fetching, 이것도 Cron 으로 Redis 값들 Loading 해서 DB에 영구적으로 저장하는 식으로 나중에 고도화
    @DeleteMapping("/{id}/like")
    public void unlikeSpot(@PathVariable Long id,
                           @AuthenticationPrincipal User user) {
        spotService.unlikeSpot(id, user);
    }

    // Spot 장소에 대한 북마크
    // 많이 이상함. 어떤 Spot ID를 받지 않아도 되나??
    @PostMapping("/{id}/bookmark")
    public void bookmarkSpot(@PathVariable Long id,
                             @AuthenticationPrincipal User user) {
        spotService.bookmarkSpot(id, user);
    }

    // 북마크 해제
    @DeleteMapping("/{id}/bookmark")
    public void unbookmarkSpot(@PathVariable Long id,
                               @AuthenticationPrincipal User user) {
        spotService.unbookmarkSpot(id, user);
    }

    // TODO : 마이페이지 유저 편의성 모아보기 ( 내가 누른, 좋아요 누른 Spot 장소, 즐겨 찾기 누른 Spot 장소 모아보기 )

    // TODO : 정해진 좋아요 수 or 좋아요 비율을 달성시 등급업 하는 로직의 컨트롤러 or Corn 이 Table 을 조회하면서 알아서 등급 해주는 그런 로직이 있으면 좋을듯함.

    // [TODO-우선순위 지금단계에선 낮음.] : 현재 가까운 Spot 추천해주기. 고도화 -> 필터링에 따라서 Spot 들을 연결해서 보여주기.
}
