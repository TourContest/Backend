package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserThemeRepository;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.spot.dto.*;
import com.goodda.jejuday.spot.entity.Bookmark;
import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Reply;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.entity.SpotViewLog;
import com.goodda.jejuday.spot.repository.BookmarkRepository;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.goodda.jejuday.spot.repository.SpotViewLogRepository;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpotServiceImpl implements SpotService {
    private final SpotRepository spotRepository;
    private final LikeRepository likeRepository;
    private final ReplyRepository replyRepository;
    private final BookmarkRepository bookmarkRepository;
    private final SpotViewLogRepository viewLogRepository;
//    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;
    private final UserThemeRepository userThemeRepository;

    @Value("${storage.bucket-name}")
    private String bucketName;

    @Value("${storage.public-url}")
    private String storagePublicUrl;

    private final AmazonS3 amazonS3;
    private final UserService userService;

    // 지도용: SPOT, CHALLENGE 만
    private static final Iterable<Spot.SpotType> MAP_VISIBLE =
            Arrays.asList(Spot.SpotType.SPOT, Spot.SpotType.CHALLENGE);

    // 커뮤니티 페이지용: 모든 타입
    private static final Iterable<Spot.SpotType> ALL_TYPES =
            Arrays.asList(Spot.SpotType.values());

    // 1
    @Override
    public List<NearSpotResponse> getNearbySpots(BigDecimal lat, BigDecimal lng, int radiusKm) {
        return spotRepository.findWithinRadius(lat, lng, radiusKm).stream()
                .filter(s -> s.getType() == Spot.SpotType.SPOT || s.getType() == Spot.SpotType.CHALLENGE)
                .map(s -> NearSpotResponse.fromEntity(
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
                                (int) likeRepository.countBySpotId(spot.getId()),
                                false
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


    @Transactional
    @Override
    public Long createSpot(SpotCreateRequestDTO req, List<MultipartFile> images) {
        Long id = createCore(req);
        if (images != null && images.size() > 3)
            throw new IllegalArgumentException("이미지는 최대 3장까지 업로드 가능합니다.");

        if (images != null && !images.isEmpty()) {
            Spot spot = spotRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Spot not found"));
            List<String> urls = uploadAll(id, images);
            spot.setImagesOrdered(urls);
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

        applyBasics(s, req);

        List<String> keep = normalizeKeep(req.getKeepImageUrls(), s.getImageUrls());
        if (newImages != null && newImages.size() > 3)
            throw new IllegalArgumentException("이미지는 요청당 최대 3장까지 업로드 가능합니다.");
        List<String> uploaded = (newImages == null || newImages.isEmpty()) ? List.of() : uploadAll(s.getId(), newImages);

        if (keep.size() + uploaded.size() > 3)
            throw new IllegalArgumentException("이미지는 최대 3장까지만 저장할 수 있습니다.");

        List<String> finalList = new ArrayList<>(keep);
        finalList.addAll(uploaded);

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
        s.setTag1(normalizeTag(tag1));
        s.setTag2(normalizeTag(tag2));
        s.setTag3(normalizeTag(tag3));
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

    @Override
    @Transactional
    public SpotDetailResponse getSpotDetail(Long id) {
        User user = securityUtil.getAuthenticatedUser();

        Spot s = spotRepository.findDetailWithUserAndTagsById(id)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));

        SpotViewLog log = new SpotViewLog();
        log.setSpot(s);
        log.setUserId(user.getId());
        log.setViewedAt(LocalDateTime.now());
        viewLogRepository.save(log);

        s.setViewCount(s.getViewCount() + 1);
        spotRepository.save(s);

        int likeCount = s.getLikeCount();
        boolean liked = likeRepository.existsByUserAndSpot(user, s);
        boolean bookmarked = bookmarkRepository.existsByUserIdAndSpotId(user.getId(), id);
        return new SpotDetailResponse(s, likeCount, liked, bookmarked);
    }

    @Transactional
    @Override
    public void deleteSpot(Long id) {
        User user = securityUtil.getAuthenticatedUser();
        Spot s = spotRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));
        if (!Objects.equals(s.getUser().getId(), user.getId()))
            throw new SecurityException("본인의 Spot 만 삭제할 수 있습니다.");

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

        if ( ! likeRepository.existsByUserAndSpot(current, spot) ) {
            likeRepository.save(new Like(current, spot, Like.TargetType.SPOT));
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

        likeRepository.findByUserAndSpot(current, spot)
                .ifPresent(like -> {
                    likeRepository.delete(like);
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
        return storagePublicUrl + "/" + key;
    }

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

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<SpotResponse> getMyPosts(Pageable pageable, String sort) {
        User user = securityUtil.getAuthenticatedUser();
        Page<Spot> spots;

        switch (sort != null ? sort.toLowerCase() : "latest") {
            case "views":
                spots = spotRepository.findByUserIdOrderByViewCountDesc(user.getId(), pageable);
                break;
            case "comments":
                spots = spotRepository.findByUserIdOrderByCreatedAtDescForCommentSort(user.getId(), pageable);
                break;
            case "latest":
            default:
                spots = spotRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
                break;
        }

        List<Long> spotIds = spots.getContent().stream().map(Spot::getId).collect(Collectors.toList());
        Set<Long> likedSpotIds = new HashSet<>();
        if (!spotIds.isEmpty()) {
            likedSpotIds = new HashSet<>(likeRepository.findLikedTargetIds(
                    user.getId(), spotIds, Like.TargetType.SPOT));
        }

        final Set<Long> finalLikedSpotIds = likedSpotIds;
        return spots.map(spot -> SpotResponse.fromEntity(
                spot,
                (int) likeRepository.countBySpotId(spot.getId()),
                finalLikedSpotIds.contains(spot.getId())
        ));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<ReplyDTO> getMyComments(Pageable pageable) {
        User user = securityUtil.getAuthenticatedUser();
        Page<Reply> replies = replyRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        return replies.map(this::toReplyDTO);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<SpotResponse> getMyLikedSpots(Pageable pageable) {
        User user = securityUtil.getAuthenticatedUser();
        Page<Spot> likedSpots = likeRepository.findLikedSpotsByUserId(user.getId(), Like.TargetType.SPOT, pageable);
        return likedSpots.map(spot -> SpotResponse.fromEntity(
                spot,
                (int) likeRepository.countBySpotId(spot.getId()),
                true
        ));
    }

    private ReplyDTO toReplyDTO(Reply reply) {
        ReplyDTO dto = new ReplyDTO();
        dto.setId(reply.getId());
        dto.setContentId(reply.getContentId());
        dto.setDepth(reply.getDepth());
        dto.setParentReplyId(reply.getParentReply() != null ? reply.getParentReply().getId() : null);
        dto.setMemberId(reply.getUser().getId());
        dto.setMemberNickname(reply.getUser().getNickname());
        dto.setText(reply.getIsDeleted() ? "삭제된 댓글입니다." : reply.getText());
        dto.setRelativeTime(calculateRelativeTime(reply.getCreatedAt()));
        dto.setIsDeleted(reply.getIsDeleted());
        dto.setCreatedAt(reply.getCreatedAt());
        return dto;
    }

    private String calculateRelativeTime(LocalDateTime createdAt) {
        if (createdAt == null) return "";
        long seconds = Duration.between(createdAt, LocalDateTime.now()).getSeconds();
        if (seconds < 60) return "방금 전";
        if (seconds < 3600) return (seconds / 60) + "분 전";
        if (seconds < 86400) return (seconds / 3600) + "시간 전";
        return (seconds / 86400) + "일 전";
    }
}
