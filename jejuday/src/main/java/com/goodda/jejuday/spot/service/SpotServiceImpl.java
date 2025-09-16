package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.repository.UserThemeRepository;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.spot.dto.SpotCreateRequestDTO;
import com.goodda.jejuday.spot.dto.SpotDetailResponse;
import com.goodda.jejuday.spot.dto.SpotResponse;
import com.goodda.jejuday.spot.dto.SpotUpdateRequest;
import com.goodda.jejuday.spot.entity.*;
import com.goodda.jejuday.spot.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.goodda.jejuday.auth.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
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
    private final UserThemeRepository userThemeRepository;
    private final ChallengeParticipationRepository challengeParticipationRepository;
    private final ReplyRepository replyRepository;
    private final UserService userService;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    private final AmazonS3 amazonS3;

    // 지도용: SPOT, CHALLENGE 만
    private static final Iterable<Spot.SpotType> MAP_VISIBLE =
            Arrays.asList(Spot.SpotType.SPOT, Spot.SpotType.CHALLENGE);

    // 커뮤니티 페이지용: 모든 타입
    private static final Iterable<Spot.SpotType> ALL_TYPES =
            Arrays.asList(Spot.SpotType.values());

    // 1
    @Transactional
    @Override
    public List<SpotResponse> getNearbySpots(BigDecimal lat, BigDecimal lng, int radiusKm) {
        final var spots = spotRepository.findWithinRadius(lat, lng, radiusKm);
        final Long userId = Optional.ofNullable(securityUtil.getAuthenticatedUser())
                .map(User::getId).orElse(null);
        final var today = LocalDate.now();

        // 사용자 기준 진행중 챌린지 집합
        java.util.Set<Long> activeChallengeIds;
        if (userId != null) {
            final var challengeIds = spots.stream()
                    .filter(s -> s.getType() == Spot.SpotType.CHALLENGE)
                    .map(Spot::getId)
                    .toList();
            if (!challengeIds.isEmpty()) {
                activeChallengeIds = new java.util.HashSet<>(
                        challengeParticipationRepository.findActiveChallengeIdsForUserOnDate(userId, challengeIds, today)
                );
            } else {
				activeChallengeIds = Collections.emptySet();
			}
		} else {
			activeChallengeIds = Collections.emptySet();
		}

		return spots.stream()
                .filter(s -> s.getType() == Spot.SpotType.SPOT || s.getType() == Spot.SpotType.CHALLENGE)
                .map(s -> {
                    int likeCount = likeRepository.countByTargetIdAndTargetType(s.getId(), Like.TargetType.SPOT);
                    boolean likedByMe = (userId != null)
                            && likeRepository.existsByUserIdAndTargetTypeAndTargetId(userId, Like.TargetType.SPOT, s.getId());
                    boolean ongoing = s.getType() == Spot.SpotType.CHALLENGE
                            && userId != null
                            && !activeChallengeIds.isEmpty()
                            && activeChallengeIds.contains(s.getId());
                    return SpotResponse.fromEntity(s, likeCount, likedByMe, ongoing);
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    @Override
    public Page<SpotResponse> getLatestSpots(Pageable pageable) {
        final Long userId = Optional.ofNullable(securityUtil.getAuthenticatedUser())
                .map(User::getId).orElse(null);

        Page<Spot> page = spotRepository.findAll(pageable); // 정렬 메서드 있으시면 그대로 사용
        return page.map(spot -> {
            int likeCount = likeRepository.countByTargetIdAndTargetType(spot.getId(), Like.TargetType.SPOT);
            boolean likedByMe = (userId != null) && likeRepository
                    .existsByUserIdAndSpotId(userId, spot.getId());
            return SpotResponse.fromEntity(spot, likeCount, likedByMe); // ✅ 진행여부 계산 안 함
        });
    }

    // 좋아요순 스팟 목록 조회
    @Transactional
    @Override
    public Page<SpotResponse> getMostViewedSpots(Pageable pageable) {
        final Long userId = Optional.ofNullable(securityUtil.getAuthenticatedUser())
                .map(User::getId).orElse(null);

        Page<Spot> page = spotRepository.findAll(pageable);
        return page.map(spot -> {
            int likeCount = likeRepository.countByTargetIdAndTargetType(spot.getId(), Like.TargetType.SPOT);
            boolean likedByMe = (userId != null) && likeRepository
                    .existsByUserIdAndSpotId(userId, spot.getId());
            return SpotResponse.fromEntity(spot, likeCount, likedByMe);
        });
    }

    // 조회수순 스팟 목록 조회
    @Transactional
    @Override
    public Page<SpotResponse> getMostLikedSpots(Pageable pageable) {
        final Long userId = Optional.ofNullable(securityUtil.getAuthenticatedUser())
                .map(User::getId).orElse(null);

        Page<Spot> page = spotRepository.findAll(pageable);
        return page.map(spot -> {
            int likeCount = likeRepository.countByTargetIdAndTargetType(spot.getId(), Like.TargetType.SPOT);
            boolean likedByMe = (userId != null) && likeRepository
                    .existsByUserIdAndSpotId(userId, spot.getId());
            return SpotResponse.fromEntity(spot, likeCount, likedByMe);
        });
    }


    @Transactional
    @Override
    public Long createSpot(SpotCreateRequestDTO req, List<MultipartFile> images) {
        Long id = createCore(req); // 텍스트/위치 저장 (기존 로직)
        if (images != null && images.size() > 3)
            throw new IllegalArgumentException("이미지는 최대 3장까지 업로드 가능합니다.");

        if (images != null && !images.isEmpty()) {
            Spot spot = spotRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Spot not found"));
            List<String> urls = uploadAll(id, images);
            spot.setImagesOrdered(urls); // img1~img3 세팅
            spotRepository.save(spot);
        }
        return id;
    }

    @Transactional
    @Override
    public void updateSpot(Long id, SpotUpdateRequest req, List<MultipartFile> newImages) {
        User user = securityUtil.getAuthenticatedUser();
        Spot s = spotRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Spot not found"));
        if (!Objects.equals(s.getUser().getId(), user.getId()))
            throw new SecurityException("본인의 Spot만 수정할 수 있습니다.");

        // 텍스트/위치/태그/테마 업데이트
        applyBasics(s, req);

        // 이미지 합성
        List<String> keep = normalizeKeep(req.getKeepImageUrls(), s.getImageUrls());
        if (newImages != null && newImages.size() > 3)
            throw new IllegalArgumentException("이미지는 요청당 최대 3장까지 업로드 가능합니다.");
        List<String> uploaded = (newImages == null || newImages.isEmpty()) ? List.of() : uploadAll(s.getId(), newImages);

        if (keep.size() + uploaded.size() > 3)
            throw new IllegalArgumentException("이미지는 최대 3장까지만 저장할 수 있습니다.");

        List<String> finalList = new ArrayList<>(keep);
        finalList.addAll(uploaded);

        // 빠진 기존 이미지는 S3에서 정리 (Spot 삭제는 소프트이므로 S3 보존이지만, 업데이트 시 제거는 정리)
        Set<String> finalSet = new HashSet<>(finalList);
        for (String oldUrl : s.getImageUrls()) {
            if (!finalSet.contains(oldUrl)) {
                userService.deleteFile(oldUrl);
            }
        }

        s.setImagesOrdered(finalList);
        spotRepository.save(s);
    }

    private void applyTheme(Spot s, Long themeId) {
        if (themeId == null) { s.setTheme(null); return; }
        s.setTheme(userThemeRepository.findById(themeId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid themeId: " + themeId)));
    }

    private void applyTags(Spot s, String tag1, String tag2, String tag3) {
        // 정규화: 앞의 '#' 제거, trim, 빈문자 -> null, 길이 제한
        s.setTag1(normalizeTag(tag1));
        s.setTag2(normalizeTag(tag2));
        s.setTag3(normalizeTag(tag3));

        // (선택) 중복 제거: 같은 태그 중복 시 하나만 남기고 뒤를 null 처리
        dedupeTags(s);
    }

    private String normalizeTag(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.startsWith("#")) t = t.substring(1).trim();
        if (t.isEmpty()) return null;
        if (t.length() > 50) t = t.substring(0, 50);
        return t;
    }

    private void dedupeTags(Spot s) {
        Set<String> seen = new HashSet<>();
        String t1 = s.getTag1(), t2 = s.getTag2(), t3 = s.getTag3();
        s.setTag1(keepOrNull(seen, t1));
        s.setTag2(keepOrNull(seen, t2));
        s.setTag3(keepOrNull(seen, t3));
    }
    private String keepOrNull(Set<String> seen, String v) {
        if (v == null) return null;
        String key = v.toLowerCase();
        if (seen.add(key)) return v;
        return null;
    }

    // 스팟 상세 조회 (뷰 증가 로직 포함)
    @Override
    public SpotDetailResponse getSpotDetail(Long spotId) {
        Long currentUserId = userService.getAuthenticatedUserId();
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));

        boolean likedByMe = currentUserId != null
                && likeRepository.existsBySpotIdAndUserId(spot.getId(), currentUserId);

        LocalDate today = LocalDate.now();
        boolean challengeOngoing = false;
        if (spot.getType() == Spot.SpotType.CHALLENGE && currentUserId != null) {
            challengeOngoing = challengeParticipationRepository
                    .existsByUser_IdAndChallenge_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                            currentUserId, spot.getId(),
                            ChallengeParticipation.Status.JOINED,
                            today, today
                    );
        }

        // 필요시 실제 댓글 카운터 사용. 없으면 해당 라인 유지/0 대입
        int commentCount = replyRepository.countByContentIdAndDepth(spot.getId(), 0);

        String authorNickname = (spot.getUser() != null ? spot.getUser().getNickname() : null);
        int likeCount = (int) likeRepository.countBySpot(spot);

        return new SpotDetailResponse(
                spot,
                likeCount,          // ← 첫 인자: likeCount (int)
                likedByMe,
                false,              // bookmarkedByMe 사용 안 하면 false
                challengeOngoing,
                authorNickname,
                commentCount
        );
    }



    @Transactional
    @Override
    public void deleteSpot(Long id) {
        User user = securityUtil.getAuthenticatedUser();
        Spot s = spotRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
        if (!Objects.equals(s.getUser().getId(), user.getId()))
            throw new SecurityException("본인의 Spot 만 삭제할 수 있습니다.");

        // S3 정리
        for (String url : s.getImageUrls()) {
            userService.deleteFile(url);
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
            likeRepository.save(new Like(current, spot, Like.TargetType.SPOT));
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

    private void validateImage(MultipartFile f) {
        if (f == null || f.isEmpty()) throw new IllegalArgumentException("이미지 파일이 비어있습니다.");
        String ct = f.getContentType();
        if (ct == null || !(ct.startsWith("image/"))) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }
    }

    private ObjectMetadata metadataOf(MultipartFile f) {
        ObjectMetadata md = new ObjectMetadata();
        md.setContentLength(f.getSize());
        md.setContentType(f.getContentType());
        return md;
    }


    private String putS3(MultipartFile f, String key, ObjectMetadata md) {
        try {
            amazonS3.putObject(bucketName, key, f.getInputStream(), md);
        } catch (IOException e) {
            throw new RuntimeException("S3 업로드 실패", e);
        }
        return amazonS3.getUrl(bucketName, key).toString();
    }

    // ----- 내부 유틸 -----
    private Long createCore(SpotCreateRequestDTO req) {
        User user = securityUtil.getAuthenticatedUser();
        Spot s = new Spot(req.getName(), req.getDescription(), req.getLatitude(), req.getLongitude(), user);
        s.setUserCreated(true);
        s.setIsDeleted(false);
        applyTheme(s, req.getThemeId());
        applyTags(s, req.getTag1(), req.getTag2(), req.getTag3());
        return spotRepository.save(s).getId();
    }

    private void applyBasics(Spot s, SpotUpdateRequest req) {
        s.setName(req.getName());
        s.setDescription(req.getDescription());
        s.setLatitude(req.getLatitude());
        s.setLongitude(req.getLongitude());
        applyTheme(s, req.getThemeId());
        applyTags(s, req.getTag1(), req.getTag2(), req.getTag3());
    }

    private List<String> normalizeKeep(List<String> keepUrls, List<String> current) {
        if (keepUrls == null) return List.of();
        List<String> keep = new ArrayList<>();
        for (String u : keepUrls) {
            if (u == null || u.isBlank()) continue;
            if (!current.contains(u))
                throw new IllegalArgumentException("유지하려는 이미지 URL이 현재와 일치하지 않습니다: " + u);
            if (!keep.contains(u)) keep.add(u);
        }
        return keep;
    }

    private List<String> uploadAll(Long spotId, List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        for (MultipartFile f : files) {
            validateImage(f);
            String key = "spot-images/" + spotId + "/" + UUID.randomUUID() + "-" + f.getOriginalFilename();
            urls.add(putS3(f, key, metadataOf(f)));
        }
        return urls;
    }
}