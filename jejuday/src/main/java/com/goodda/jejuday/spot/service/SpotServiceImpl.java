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
import com.goodda.jejuday.spot.repository.BookmarkRepository;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpotServiceImpl implements SpotService {
    private final SpotRepository spotRepository;
    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;
//    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;


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
    public SpotDetailResponse getSpotDetail(Long id) {
        User user = securityUtil.getAuthenticatedUser();
        Spot s = spotRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
        int likeCount = likeRepository.countByTargetIdAndTargetType(id, Like.TargetType.SPOT);
        boolean liked = likeRepository.existsByUserIdAndTargetTypeAndTargetId(user.getId(), Like.TargetType.SPOT, id);
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
    public void likeSpot(Long id) {
        User user = securityUtil.getAuthenticatedUser();
        if (!likeRepository.existsByUserIdAndTargetTypeAndTargetId(user.getId(), Like.TargetType.SPOT, id)) {
            Spot s = spotRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
            likeRepository.save(new Like(user, s, Like.TargetType.SPOT));
        }
    }

    @Override
    @Transactional
    public void unlikeSpot(Long id) {
        User user = securityUtil.getAuthenticatedUser();
        likeRepository.deleteByUserIdAndTargetTypeAndTargetId(user.getId(), Like.TargetType.SPOT, id);
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
}