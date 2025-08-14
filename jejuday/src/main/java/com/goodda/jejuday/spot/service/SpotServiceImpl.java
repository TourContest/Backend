package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.spot.dto.SpotCreateRequest;
import com.goodda.jejuday.spot.dto.SpotDetailResponse;
import com.goodda.jejuday.spot.dto.SpotResponse;
import com.goodda.jejuday.spot.dto.SpotUpdateRequest;
import com.goodda.jejuday.spot.entity.Bookmark;
import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.entity.SpotViewLog;
import com.goodda.jejuday.spot.repository.BookmarkRepository;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.goodda.jejuday.spot.repository.SpotViewLogRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpotServiceImpl implements SpotService {
    private final SpotRepository spotRepository;
    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;
    private final SpotViewLogRepository viewLogRepository;
//    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;

    // 지도용: SPOT, CHALLENGE 만
    private static final Iterable<Spot.SpotType> MAP_VISIBLE =
            Arrays.asList(Spot.SpotType.SPOT, Spot.SpotType.CHALLENGE);

    // 커뮤니티 페이지용: 모든 타입
    private static final Iterable<Spot.SpotType> ALL_TYPES =
            Arrays.asList(Spot.SpotType.values());

    // 1
    @Override
    public List<SpotResponse> getNearbySpots(BigDecimal lat, BigDecimal lng, int radiusKm) {
        return spotRepository.findWithinRadius(lat, lng, radiusKm).stream()
                .filter(s -> s.getType() == Spot.SpotType.SPOT || s.getType() == Spot.SpotType.CHALLENGE)
                .map(s -> SpotResponse.fromEntity(
                        s,
                        likeRepository.countByTargetIdAndTargetType(s.getId(), Like.TargetType.SPOT),
                        false
                ))
                .collect(Collectors.toList());
    }

    @Override
    public Page<SpotResponse> getLatestSpots(Pageable pageable) {
        return spotRepository
                .findByTypeInOrderByCreatedAtDesc(ALL_TYPES, pageable)
                .map(spot ->
                        SpotResponse.fromEntity(
                                spot,
                                (int) likeRepository.countBySpotId(spot.getId()), // 좋아요 개수
                                false // 현재 사용자가 눌렀는지 여부 (로그인 기반으로 수정 가능)
                        )
                );
    }

    @Override
    public Page<SpotResponse> getMostViewedSpots(Pageable pageable) {
        return spotRepository
                .findByTypeInOrderByViewCountDesc(ALL_TYPES, pageable)
                .map(spot ->
                        SpotResponse.fromEntity(
                                spot,
                                (int) likeRepository.countBySpotId(spot.getId()),
                                false
                        )
                );
    }

    @Override
    public Page<SpotResponse> getMostLikedSpots(Pageable pageable) {
        return spotRepository
                .findByTypeInOrderByLikeCountDesc(ALL_TYPES, pageable)
                .map(spot ->
                        SpotResponse.fromEntity(
                                spot,
                                (int) likeRepository.countBySpotId(spot.getId()),
                                false
                        )
                );
    }



    @Override
    @Transactional
    public Long createSpot(SpotCreateRequest req) {
        User user = securityUtil.getAuthenticatedUser();
        Spot s = new Spot(
                req.getName(),
                req.getDescription(),
                req.getLatitude(),
                req.getLongitude(),
                user
        );
        return spotRepository.save(s).getId();
    }


    @Override
    @Transactional
    public SpotDetailResponse getSpotDetail(Long id) {
        User user = securityUtil.getAuthenticatedUser();
        Spot s = spotRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));

        // 1) ViewLog 중계 테이블에 기록
        SpotViewLog log = new SpotViewLog();
        log.setSpot(s);
        log.setUserId(user.getId());
        log.setViewedAt(LocalDateTime.now());
        viewLogRepository.save(log);

        // 2) Spot.viewCount ++
        s.setViewCount(s.getViewCount() + 1);
        spotRepository.save(s);

        // 이후 기존처럼 DetailResponse 생성
        int likeCount = s.getLikeCount();
        boolean liked = likeRepository.existsByUserAndSpot(user, s);
        boolean bookmarked = bookmarkRepository.existsByUserIdAndSpotId(user.getId(), id);
        return new SpotDetailResponse(s, likeCount, liked, bookmarked);
    }


    @Override
    @Transactional
    public void updateSpot(Long id, SpotUpdateRequest req) {
        User user = securityUtil.getAuthenticatedUser();
        Spot s = spotRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
        if (!Objects.equals(s.getUser().getId(), user.getId())) {
            throw new SecurityException("본인의 Spot만 수정할 수 있습니다.");
        }
        s.setName(req.getName());
        s.setDescription(req.getDescription());
        s.setLatitude(req.getLatitude());
        s.setLongitude(req.getLongitude());
        spotRepository.save(s);
    }


    @Override
    @Transactional
    public void deleteSpot(Long id) {
        User user = securityUtil.getAuthenticatedUser();
        Spot s = spotRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
        if (!Objects.equals(s.getUser().getId(), user.getId())) {
            throw new SecurityException("본인의 Spot만 삭제할 수 있습니다.");
        }
        s.setIsDeleted(true);
        s.setDeletedAt(LocalDateTime.now());
        s.setDeletedBy(user.getId());
        spotRepository.save(s);
    }

    @Override
    @Transactional
    public void likeSpot(Long spotId) {
        User current = securityUtil.getAuthenticatedUser();
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));

        // 1) 중계 테이블에 기록
        if ( ! likeRepository.existsByUserAndSpot(current, spot) ) {
            likeRepository.save(new Like(current, spot));
            // 2) Spot.likeCount ++
            spot.setLikeCount(spot.getLikeCount() + 1);
            spotRepository.save(spot);
        }
    }

    @Override
    @Transactional
    public void unlikeSpot(Long spotId) {
        User current = securityUtil.getAuthenticatedUser();
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));

        // 1) 중계 테이블 삭제
        likeRepository.findByUserAndSpot(current, spot)
                .ifPresent(like -> {
                    likeRepository.delete(like);
                    // 2) Spot.likeCount --
                    spot.setLikeCount(spot.getLikeCount() - 1);
                    spotRepository.save(spot);
                });
    }

//    @Override
//    @Transactional
//    public void bookmarkSpot(Long spotId, Long userId) {
//        if (!bookmarkRepository.existsByUserIdAndSpotId(userId, spotId)) {
//            User user = userRepository.findById(userId)
//                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
//            Spot spot = spotRepository.findById(spotId)
//                    .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
//
//            bookmarkRepository.save(new Bookmark(user, spot));
//        }
//    }


    @Override
    @Transactional
    public void bookmarkSpot(Long id) {
        User user = securityUtil.getAuthenticatedUser();
        if (!bookmarkRepository.existsByUserIdAndSpotId(user.getId(), id)) {
            Spot s = spotRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
            bookmarkRepository.save(new Bookmark(user, s));
        }
    }

    @Override
    @Transactional
    public void unbookmarkSpot(Long id) {
        User user = securityUtil.getAuthenticatedUser();
        bookmarkRepository.deleteByUserIdAndSpotId(user.getId(), id);
    }

    @Override
    public Spot getSpotById(Long spotId) {
        return spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found with id: " + spotId));
    }
}