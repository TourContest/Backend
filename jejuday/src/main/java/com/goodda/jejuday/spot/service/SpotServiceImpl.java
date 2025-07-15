package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
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
//    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;

    @Override
    public List<SpotResponse> getNearbySpots(BigDecimal lat, BigDecimal lng, int radiusKm) {
        List<Spot> spots = spotRepository.findWithinRadius(lat, lng, radiusKm);
        return spots.stream()
                .map(spot -> new SpotResponse(spot.getId(), spot.getName(), spot.getLatitude(), spot.getLongitude(),
                        likeRepository.countByTargetIdAndTargetType(spot.getId(), "SPOT"), false))
                .collect(Collectors.toList());
    }

    @Override
    public SpotDetailResponse getSpotDetail(Long id, User user) {
        Spot spot = spotRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        int likeCount = likeRepository.countByTargetIdAndTargetType(id, "SPOT");
        boolean liked = likeRepository.existsByUserIdAndTargetTypeAndTargetId(user.getId(), "SPOT", id);
        boolean bookmarked = bookmarkRepository.existsByUserIdAndSpotId(user.getId(), id);
        return new SpotDetailResponse(id, spot.getName(), spot.getLatitude(), spot.getLongitude(), likeCount, liked, spot.getDescription(), new ArrayList<>(), 0, bookmarked);
    }

    @Override
    public Long createSpot(SpotCreateRequest request, User user) {
        Spot spot = new Spot(request.getName(), request.getDescription(), request.getLatitude(), request.getLongitude(), user, request.getType());
        return spotRepository.save(spot).getId();
    }

    @Override
    public void updateSpot(Long id, SpotUpdateRequest request, User user) {
        Spot spot = spotRepository.findById(id).orElseThrow();
        if (!Objects.equals(spot.getUser().getId(), user.getId())) throw new SecurityException();
        spot.setName(request.getName());
        spot.setDescription(request.getDescription());
        spot.setLatitude(BigDecimal.valueOf(request.getLatitude()));
        spot.setLongitude(BigDecimal.valueOf(request.getLongitude()));
        spotRepository.save(spot);
    }


    @Override
    public void deleteSpot(Long id, User user) {
        Spot spot = spotRepository.findById(id).orElseThrow();
        if (!Objects.equals(spot.getUser().getId(), user.getId())) throw new SecurityException();
        spot.setIsDeleted(true);
        spot.setDeletedAt(LocalDateTime.now());
        spot.setDeletedBy(user.getId());
        spotRepository.save(spot);
    }

    @Override
    public void likeSpot(Long id, User user) {
        if (!likeRepository.existsByUserIdAndTargetTypeAndTargetId(user.getId(), "SPOT", id)) {
            Spot spot = spotRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
            likeRepository.save(new Like(user, spot, Like.TargetType.SPOT));
        }
    }

    @Override
    public void unlikeSpot(Long id, User user) {
        likeRepository.deleteByUserIdAndTargetTypeAndTargetId(user.getId(), "SPOT", id);
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
    public void bookmarkSpot(Long spotId, User user) {
        if (!bookmarkRepository.existsByUserIdAndSpotId(user.getId(), spotId)) {
            Spot spot = spotRepository.findById(spotId)
                    .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
            bookmarkRepository.save(new Bookmark(user, spot));
        }
    }

    @Override
    public void unbookmarkSpot(Long id, User user) {
        bookmarkRepository.deleteByUserIdAndSpotId(user.getId(), id);
    }
}