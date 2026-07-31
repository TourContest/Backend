package com.goodda.jejuday.spot.controller;

import com.goodda.jejuday.auth.dto.ApiResponse;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.dto.*;
import com.goodda.jejuday.spot.service.SpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Spot", description = "위치 마커(스팟) 조회/등록/수정/삭제 및 좋아요·북마크 API")
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
    // TODO : 검색 조회랑 합칠 필요가 있음. 09/02
    @Operation(summary = "근처 위치 마커 조회", description = "위도/경도 기준 반경(radiusKm, 기본 5km) 내 삭제되지 않은 스팟 목록을 조회합니다. 홈 화면 지도 마커 표시에 사용됩니다.")
    @Description("(1) 홈 화면 > 위치 마커 조회")
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<NearSpotResponse>>> getNearby(
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
    @Operation(summary = "최신순 스팟 목록 조회", description = "등록일(createdAt) 기준 최신순으로 스팟을 페이징 조회합니다. 주간제주 탭 진입 시 기본으로 호출됩니다.")
    @Description("(3) 주간제주 > (default) 최신순으로 스팟 조회")
    @GetMapping("/latest")
    public Page<SpotResponse> latest(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return spotService.getLatestSpots(pageable);
    }

    // 2) 인기순으로 위치 마커 - reddit 알고리즘 적용 - redis 적용.
    
    // 3-1) 조회수순으로 위치 마커
    @Operation(summary = "조회순 스팟 목록 조회", description = "조회수(viewCount) 기준 내림차순으로 스팟을 페이징 조회합니다. 주간제주 탭의 정렬 필터로 사용됩니다.")
    @Description("(3) 주간제주 > (필터) 조회순으로 스팟 조회")
    @GetMapping("/most-viewed")
    public Page<SpotResponse> mostViewed(
            @ParameterObject
            @PageableDefault(size = 20, sort = "viewCount", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return spotService.getMostViewedSpots(pageable);
    }

    // 3-2) 좋아요순으로 위치 마커
    @Operation(summary = "좋아요순 스팟 목록 조회", description = "좋아요 수(likeCount) 기준 내림차순으로 스팟을 페이징 조회합니다. 주간제주 탭의 정렬 필터로 사용됩니다.")
    @Description("(3) 주간제주 > (필터) 좋아요순으로 스팟 조회")
    @GetMapping("/most-liked")
    public Page<SpotResponse> mostLiked(
            @ParameterObject
            @PageableDefault(size = 20, sort = "likeCount", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return spotService.getMostLikedSpots(pageable);
    }

    // 3-3.
    // 1) 홈-위치 마커 클릭 시 상세 정보 보여주기
    // 2) 주간제주-게시글 클릭 시 상세 정보 보여주기
    @GetMapping("/{id}")
    @Operation(summary = "스팟 상세 조회", description = "스팟 상세 정보를 조회합니다. 호출 시 spot_view_log에 기록되며 조회수가 1 증가합니다.")
    @Description("(1) 홈화면 (3) 주간제주 > spot 장소 상세")
    @Schema( description = "Spot 장소 상세 정보 조회 -> spot_view_log table 에 조회수 증가 로직 포함" )
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
    @Operation(summary = "스팟 등록", description = "유저가 새로운 스팟(장소)을 등록합니다. multipart/form-data로 data(JSON)와 최대 3장의 images를 함께 전송합니다. 최초 등록 시 SpotType은 POST로 저장됩니다.")
    @Description("(3) 주간제주 > 유저 spot 장소 등록")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Long>> create(
            @RequestPart("data") @Valid SpotCreateRequestDTO req,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        Long id = spotService.createSpot(req, images); // 내부에서 3장 제한 체크
        return ResponseEntity.ok(ApiResponse.onSuccess(id));
    }

    // TODO 위치 마커 수정할때 전에 등록했던 정보를 가지고 오는 Controller 추가 필요
    // TODO : 삭제된 위치 마커에 대해서 수정? 말이 안됨. isDeleted flag 가 따라서 아예 거절 하도록.
    // 사용자 위치 마커 수정 ( 위치가 잘못 되었을 때, or 내용 수정을 원할때 )
    @Operation(summary = "스팟 수정", description = "유저가 등록한 스팟의 정보(위치, 내용, 이미지 등)를 수정합니다. 유지할 이미지 + 신규 이미지를 합쳐 최종 3장 이하로 제한됩니다.")
    @Description("(3) 주간제주 (4) 마이페이지 > 유저 spot 장소 등록")
    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> update(
            @PathVariable Long id,
            @RequestPart("data") @Valid SpotUpdateRequest req,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        spotService.updateSpot(id, req, images); // keep + new 합쳐서 최종 ≤ 3장
        return ResponseEntity.noContent().build();
    }

    // 커뮤니티에 등록된 Spot 장소 삭제,
    // TODO : Default 로 지금은 모든 위치 마커 삭제로 놔두었지만, 등급업 된 Spot 장소의 경우에 삭제를 허용 할지 말지 추가 의논 필요.
    @Operation(summary = "스팟 삭제", description = "유저가 등록한 스팟을 삭제합니다.")
    @Description("(3) 주간제주 (4) 마이페이지 > 유저 spot 장소 수정")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        spotService.deleteSpot(id);
        return ResponseEntity.noContent().build();
    }

    // Spot 장소에 대한 좋아요.
    // TODO : 한번더 누르면 취소와 합쳐질 수 있음. front end 개발에 따라서 수정될 가능성 높음. 또한 redis 를 이용한 느린 참조
    @Operation(summary = "스팟 좋아요 등록", description = "스팟에 좋아요를 등록합니다.")
    @Description("(1) 홈화면 (3) 주간제주 > 좋아요 등록 ")
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> like(@PathVariable Long id) {
        spotService.likeSpot(id);
        return ResponseEntity.ok().build();
    }

    // 한번더 누르면 취소됨.
    // TODO : 지금은 이렇게 간단하게 해놓고 나중에 Redis 통해서 조회수 같은 것들 Lazy Fetching, 이것도 Cron 으로 Redis 값들 Loading 해서 DB에 영구적으로 저장하는 식으로 나중에 고도화
    @Operation(summary = "스팟 좋아요 취소", description = "스팟에 등록한 좋아요를 취소합니다.")
    @Description("(1) 홈화면 (3) 주간제주 > 좋아요 취소 ")
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlike(@PathVariable Long id) {
        spotService.unlikeSpot(id);
        return ResponseEntity.noContent().build();
    }

    // 삭제하는 걸로.
    // Spot 장소에 대한 북마크
    // 많이 이상함. 어떤 Spot ID를 받지 않아도 되나??
    @Operation(summary = "스팟 북마크 등록", description = "스팟을 북마크(즐겨찾기)에 등록합니다.")
    @Description("(1) 홈화면 (3) 주간제주 > 북마크 등록 ")
    @PostMapping("/{id}/bookmark")
    public ResponseEntity<Void> bookmark(@PathVariable Long id) {
        spotService.bookmarkSpot(id);
        return ResponseEntity.ok().build();
    }

    // 북마크 해제
    @Operation(summary = "스팟 북마크 해제", description = "스팟에 등록한 북마크(즐겨찾기)를 해제합니다.")
    @Description("(1) 홈화면 (3) 주간제주 > 북마크 등록 해제 ")
    @DeleteMapping("/{id}/bookmark")
    public ResponseEntity<Void> unbookmark(@PathVariable Long id) {
        spotService.unbookmarkSpot(id);
        return ResponseEntity.noContent().build();
    }

    // TODO : 마이페이지 유저 편의성 모아보기 ( 내가 누른, 좋아요 누른 Spot 장소, 즐겨 찾기 누른 Spot 장소 모아보기 )

    // 근처 추천은 SpotRecommendationController#recommend (GET /api/spots/{spotId}/nearby-recommendations) 로 구현됨
}
