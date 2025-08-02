package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.dto.SpotCreateRequest;
import com.goodda.jejuday.spot.dto.SpotDetailResponse;
import com.goodda.jejuday.spot.dto.SpotResponse;
import com.goodda.jejuday.spot.dto.SpotUpdateRequest;
import com.goodda.jejuday.spot.service.SpotService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/spots")
@RequiredArgsConstructor
public class SpotController {
    private final SpotService spotService;


    /* 1. [바텀네비게이션 (1) 홈 화면] 에서 위치 마커 띄우는 3안
     * 1안 : 누를때마다 요청과 응답 <- 이걸로 구현되어 있음.
     * 2안 : 로그인 하면서 지도 다 받아오고 일정 주기로 변경된게 있는지 polling
     * 3안 : 기억 안남
     *
     * 근방 몇 km 까지 요청할지 : 유저가 결정, Where 절에 넣어서 필터링, 근방 몇 km 까지?
     */
    // // 홈화면에서 뛰울 위치 기반 위치 마커 read
    // 삭제된 위치 마커 빼고 뛰우는 방식으로.
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<SpotResponse>>> getNearby(
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            @RequestParam(defaultValue = "5") int radiusKm
    ) {
        return ResponseEntity.ok(
                ApiResponse.onSuccess( spotService.getNearbySpots(lat, lng, radiusKm) )
        );
    }



    // 2. [바텀네비게이션 (3) 주간제주 화면]
    // 1) 최신순으로 위치 마커 [ 바텀네비게이션 주간제주 아이콘 클릭시 전달할 default data ]
    // TODO : 무한 스크롤로 구현 할지 페이징 네이션으로 할지 정해야됨.
    // 몇개까지 page 를 보낼지 정해야됨.
    @GetMapping("/api/spots/latest")
    public Page<SpotResponse> latest(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return spotService.getLatestSpots(pageable);
    }


    // 2) 인기순으로 위치 마커 - reddit 알고리즘 적용 - redis 적용.
    
    
    // 3-1) 조회수순으로 위치 마커
    @GetMapping("/api/spots/most-viewed")
    public Page<SpotResponse> mostViewed(
            @ParameterObject
            @PageableDefault(size = 20, sort = "viewCount", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return spotService.getMostViewedSpots(pageable);
    }

    // 3-2) 좋아요순으로 위치 마커
    @GetMapping("/api/spots/most-liked")
    public Page<SpotResponse> mostLiked(
            @ParameterObject
            @PageableDefault(size = 20, sort = "likeCount", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return spotService.getMostLikedSpots(pageable);
    }

    // 3-3. 위치 마커 클릭 시 상세 정보 보여주기
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SpotDetailResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.onSuccess( spotService.getSpotDetail(id) )
        );
    }

    // 4. user - Spot 장소 등록
    /*
     * 처음 user 가 등록한 장소는 무조건 SpotType 이 POST 로 저장됨.
     * 추가적으로 테마가 들어가야 됨.
     * 테마 enum 으로 설정하고 그 중에서 고를수 있게.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> create(@RequestBody SpotCreateRequest req) {
        Long id = spotService.createSpot(req);
        return ResponseEntity.ok(ApiResponse.onSuccess(id));
    }
    
    // TODO : 관리자 페이지에서 Spot 장소 등록, Spot 장소 등급업, Spot 장소 삭제

    // TODO 위치 마커 수정할때 전에 등록했던 정보를 가지고 오는 Controller 추가 필요
    // TODO : 삭제된 위치 마커에 대해서 수정? 말이 안됨. isDeleted flag 가 따라서 아예 거절 하도록.
    // 사용자 위치 마커 수정 ( 위치가 잘못 되었을 때, or 내용 수정을 원할때 )
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable Long id,
            @RequestBody SpotUpdateRequest req
    ) {
        spotService.updateSpot(id, req);
        return ResponseEntity.noContent().build();
    }

    // 커뮤니티에 등록된 Spot 장소 삭제,
    // TODO : Default 로 지금은 모든 위치 마커 삭제로 놔두었지만, 등급업 된 Spot 장소의 경우에 삭제를 허용 할지 말지 추가 의논 필요.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        spotService.deleteSpot(id);
        return ResponseEntity.noContent().build();
    }

    // Spot 장소에 대한 좋아요.
    // TODO : 한번더 누르면 취소와 합쳐질 수 있음. front end 개발에 따라서 수정될 가능성 높음. 또한 redis 를 이용한 느린 참조
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> like(@PathVariable Long id) {
        spotService.likeSpot(id);
        return ResponseEntity.ok().build();
    }

    // 한번더 누르면 취소됨.
    // TODO :: 지금은 이렇게 간단하게 해놓고 나중에 Redis 통해서 조회수 같은 것들 Lazy Fetching, 이것도 Cron 으로 Redis 값들 Loading 해서 DB에 영구적으로 저장하는 식으로 나중에 고도화
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlike(@PathVariable Long id) {
        spotService.unlikeSpot(id);
        return ResponseEntity.noContent().build();
    }


    // 삭제하는 걸로.
    // Spot 장소에 대한 북마크
    // 많이 이상함. 어떤 Spot ID를 받지 않아도 되나??
    @PostMapping("/{id}/bookmark")
    public ResponseEntity<Void> bookmark(@PathVariable Long id) {
        spotService.bookmarkSpot(id);
        return ResponseEntity.ok().build();
    }

    // 북마크 해제
    @DeleteMapping("/{id}/bookmark")
    public ResponseEntity<Void> unbookmark(@PathVariable Long id) {
        spotService.unbookmarkSpot(id);
        return ResponseEntity.noContent().build();
    }

    // TODO : 마이페이지 유저 편의성 모아보기 ( 내가 누른, 좋아요 누른 Spot 장소, 즐겨 찾기 누른 Spot 장소 모아보기 )

    // TODO : 정해진 좋아요 수 or 좋아요 비율을 달성시 등급업 하는 로직의 컨트롤러 or Corn 이 Table 을 조회하면서 알아서 등급 해주는 그런 로직이 있으면 좋을듯함.

    // [TODO-우선순위 지금단계에선 낮음.] : 현재 가까운 Spot 추천해주기. 고도화 -> 필터링에 따라서 Spot 들을 연결해서 보여주기.
}
