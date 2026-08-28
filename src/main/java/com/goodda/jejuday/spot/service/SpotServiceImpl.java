package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserThemeRepository;
import com.goodda.jejuday.auth.service.UserBlockService;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.notification.service.NotificationFactory;
import com.goodda.jejuday.notification.service.NotificationService;
import com.goodda.jejuday.common.ImageValidator;
import com.goodda.jejuday.spot.ranking.EngagementChangedEvent;
import com.goodda.jejuday.spot.ranking.SpotRankingConstants;
import com.goodda.jejuday.spot.dto.*;
import com.goodda.jejuday.spot.entity.Bookmark;
import com.goodda.jejuday.spot.entity.ChallengeRecoItem;
import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Reply;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.entity.SpotDetail;
import com.goodda.jejuday.spot.entity.SpotViewLog;
import com.goodda.jejuday.spot.repository.BookmarkRepository;
import com.goodda.jejuday.spot.repository.ChallengeRecoItemRepository;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotDetailRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.goodda.jejuday.spot.repository.SpotViewLogRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.goodda.jejuday.auth.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
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
    private final ChallengeRecoItemRepository challengeRecoItemRepository;
    private final SpotDetailRepository spotDetailRepository;
    private final UserBlockService userBlockService;
    private final NotificationService notificationService;
//    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;
    private final UserThemeRepository userThemeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    private final AmazonS3 amazonS3;
    private final UserService userService;
    private final RedisTemplate<String, String> redisTemplate;

    // 지도용: SPOT, CHALLENGE 만
    private static final Iterable<Spot.SpotType> MAP_VISIBLE =
            Arrays.asList(Spot.SpotType.SPOT, Spot.SpotType.CHALLENGE);

    // 커뮤니티 페이지용: 모든 타입
    private static final Iterable<Spot.SpotType> ALL_TYPES =
            Arrays.asList(Spot.SpotType.values());

    /**
     * POST → SPOT 승격까지 좋아요 환산 기준 남은 개수. SpotPromotionService가 실제 승격 판정에
     * 쓰는 것과 같은 Redis ENGAGEMENT_KEY 점수를 그대로 읽어서, 여기 표시되는 값과 실제 승격 시점이
     * 어긋나지 않게 한다(댓글도 점수에 들어가지만 화면 문구는 "좋아요 N개"로 단순화해서 보여준다).
     */
    private Integer likesUntilPromotion(Spot spot, Map<Long, Double> engagementScores) {
        if (spot.getType() != Spot.SpotType.POST) return null;

        double current = engagementScores.getOrDefault(spot.getId(), 0.0);
        double remaining = SpotRankingConstants.POST_TO_SPOT_ENGAGEMENT_FLOOR - current;
        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / SpotRankingConstants.LIKE_WEIGHT);
    }

    private Integer likesUntilPromotion(Spot spot) {
        if (spot.getType() != Spot.SpotType.POST) return null;
        Double engagement = redisTemplate.opsForZSet()
                .score(SpotRankingConstants.ENGAGEMENT_KEY, "community:" + spot.getId());
        return likesUntilPromotion(spot, engagement == null ? Map.of() : Map.of(spot.getId(), engagement));
    }

    /** 목록 조회 시 스팟마다 댓글 수를 따로 조회하는 N+1을 막기 위해 페이지 단위로 한 번에 조회한다. */
    private Map<Long, Integer> commentCounts(List<Spot> spots) {
        List<Long> spotIds = spots.stream().map(Spot::getId).toList();
        if (spotIds.isEmpty()) return Map.of();

        Map<Long, Integer> out = new HashMap<>();
        for (Object[] row : replyRepository.countGroupByContentIds(spotIds, 0)) {
            out.put((Long) row[0], ((Long) row[1]).intValue());
        }
        return out;
    }

    /**
     * 목록 조회 시 POST 타입 스팟마다 ZSCORE를 따로 호출하던 N+1을 파이프라이닝으로 한 번에 조회한다
     * (SpotRankingConstants.ENGAGEMENT_KEY는 StringRedisSerializer라 UTF-8 바이트로 직접 인코딩).
     */
    private Map<Long, Double> engagementScores(List<Spot> spots) {
        List<Long> postIds = spots.stream()
                .filter(s -> s.getType() == Spot.SpotType.POST)
                .map(Spot::getId)
                .toList();
        if (postIds.isEmpty()) return Map.of();

        byte[] key = SpotRankingConstants.ENGAGEMENT_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long id : postIds) {
                connection.zSetCommands().zScore(key, ("community:" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return null;
        });

        Map<Long, Double> out = new HashMap<>();
        for (int i = 0; i < postIds.size(); i++) {
            Object value = results.get(i);
            if (value instanceof Double) out.put(postIds.get(i), (Double) value);
        }
        return out;
    }

    // 1
    @Override
    public List<NearSpotResponse> getNearbySpots(BigDecimal lat, BigDecimal lng, int radiusKm) {
        // 로그인한 유저에게 현재 "챌린지 후보(upcoming)"로 추천중인 스팟은 챌린지 탭에서 이미
        // 노출되므로, 지도의 일반 스팟 목록에서는 제외해 같은 장소가 두 번 보이지 않게 한다.
        Set<Long> upcomingChallengeSpotIds = currentUserUpcomingChallengeSpotIds();
        List<Long> blockedUserIds = userBlockService.getBlockedUserIdsOrSentinel();

        return spotRepository.findWithinRadius(lat, lng, radiusKm).stream()
                .filter(s -> s.getType() == Spot.SpotType.SPOT || s.getType() == Spot.SpotType.CHALLENGE)
                .filter(s -> !upcomingChallengeSpotIds.contains(s.getId()))
                .filter(s -> s.getUser() == null || !blockedUserIds.contains(s.getUser().getId()))
                // 스팟별로 좋아요 COUNT 쿼리를 따로 날리던 N+1 - 다른 목록 API처럼 이미 원자적으로
                // 갱신되는 Spot.likeCount 비정규화 카운터를 그대로 쓴다.
                .map(s -> NearSpotResponse.fromEntity(s, s.getLikeCount(), false))
                .collect(Collectors.toList());
    }

    /** 비로그인 요청은 개인화 대상이 없으므로 빈 목록 반환 */
    private Set<Long> currentUserUpcomingChallengeSpotIds() {
        Long userId;
        try {
            userId = securityUtil.getAuthenticatedUser().getId();
        } catch (Exception e) {
            return Set.of();
        }
        return challengeRecoItemRepository.findActiveByUser(userId, LocalDateTime.now()).stream()
                .map(ChallengeRecoItem::getSpotId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public Page<SpotResponse> getLatestSpots(Pageable pageable) {
        Page<Spot> page = spotRepository
                .findByTypeInOrderByCreatedAtDesc(ALL_TYPES, userBlockService.getBlockedUserIdsOrSentinel(), pageable);
        Map<Long, Double> scores = engagementScores(page.getContent());
        Map<Long, Integer> commentCounts = commentCounts(page.getContent());
        return page.map(spot ->
                SpotResponse.fromEntity(
                        spot,
                        spot.getLikeCount(), // 좋아요 개수
                        false, // 현재 사용자가 눌렀는지 여부 (로그인 기반으로 수정 가능)
                        likesUntilPromotion(spot, scores),
                        commentCounts.getOrDefault(spot.getId(), 0)
                )
        );
    }

    @Override
    public Page<SpotResponse> getMostViewedSpots(Pageable pageable) {
        Page<Spot> page = spotRepository
                .findByTypeInOrderByViewCountDesc(ALL_TYPES, userBlockService.getBlockedUserIdsOrSentinel(), pageable);
        Map<Long, Double> scores = engagementScores(page.getContent());
        Map<Long, Integer> commentCounts = commentCounts(page.getContent());
        return page.map(spot ->
                SpotResponse.fromEntity(spot, spot.getLikeCount(), false, likesUntilPromotion(spot, scores),
                        commentCounts.getOrDefault(spot.getId(), 0))
        );
    }

    @Override
    public Page<SpotResponse> getMostLikedSpots(Pageable pageable) {
        Page<Spot> page = spotRepository
                .findByTypeInOrderByLikeCountDesc(ALL_TYPES, userBlockService.getBlockedUserIdsOrSentinel(), pageable);
        Map<Long, Double> scores = engagementScores(page.getContent());
        Map<Long, Integer> commentCounts = commentCounts(page.getContent());
        return page.map(spot ->
                SpotResponse.fromEntity(spot, spot.getLikeCount(), false, likesUntilPromotion(spot, scores),
                        commentCounts.getOrDefault(spot.getId(), 0))
        );
    }


    @Override
    public Long createSpot(SpotCreateRequestDTO req, List<MultipartFile> images) {
        if (images != null && images.size() > 3)
            throw new IllegalArgumentException("이미지는 최대 3장까지 업로드 가능합니다.");

        // 트랜잭션(DB 커넥션 점유) 안에서 이미지마다 순차로 S3 PUT을 기다리던 게 글쓰기가 느렸던
        // 원인이라 텍스트/위치 저장(createCore)과 S3 업로드를 분리하고, 업로드는 병렬로 돌린다.
        Long id = createCore(req);
        if (images != null && !images.isEmpty()) {
            try {
                List<String> urls = uploadAll(id, images);
                setSpotImages(id, urls);
            } catch (RuntimeException e) {
                // 이미지 없는 반쪽짜리 게시글이 남지 않도록, 업로드 실패 시 방금 만든 글을 정리한다.
                spotRepository.deleteById(id);
                throw e;
            }
        }
        return id;
    }

    // 같은 클래스 안에서 호출돼 프록시를 안 타므로 @Transactional을 붙여도 적용 안 됨 - findById/save가
    // 각각 자체 트랜잭션으로 원자적이라 두 단계로 나눠도 무방하다(둘 사이 그 row를 건드릴 동시 요청이
    // 현실적으로 없음).
    private void setSpotImages(Long id, List<String> urls) {
        Spot spot = spotRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Spot not found"));
        spot.setImagesOrdered(urls); // img1~img3 세팅
        spotRepository.save(spot);
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
        // - 삭제도 순차 호출이면 장 수만큼 왕복이 더해지니 병렬로 보낸다.
        Set<String> finalSet = new HashSet<>(finalList);
        List<CompletableFuture<Void>> deletions = s.getImageUrls().stream()
                .filter(oldUrl -> !finalSet.contains(oldUrl))
                .map(oldUrl -> CompletableFuture.runAsync(() -> userService.deleteFile(oldUrl)))
                .toList();
        deletions.forEach(CompletableFuture::join);

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

    @Override
    @Transactional
    public SpotDetailResponse getSpotDetail(Long id) {
        User user = securityUtil.getAuthenticatedUser();

        // 테마/태그까지 한 번에 패치
        Spot s = spotRepository.findDetailWithUserAndTagsById(id)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found"));

        // 1) ViewLog 기록
        SpotViewLog log = new SpotViewLog();
        log.setSpot(s);
        log.setUserId(user.getId());
        log.setViewedAt(LocalDateTime.now());
        viewLogRepository.save(log);

        // 2) viewCount++
        s.setViewCount(s.getViewCount() + 1);
        spotRepository.save(s);

        // 3) 응답 생성
        int likeCount = s.getLikeCount();
        boolean liked = likeRepository.existsByUserAndSpot(user, s);
        boolean bookmarked = bookmarkRepository.existsByUserIdAndSpotId(user.getId(), id);
        int commentCount = replyRepository.countByContentIdAndDepth(s.getId(), 0);
        return new SpotDetailResponse(s, likeCount, liked, bookmarked, likesUntilPromotion(s), resolveDescription(s), commentCount);
    }

    // 유저가 직접 쓴 description이 없으면(공식 관광지 대부분) TourAPI에서 동기화해온 overview로 대체한다.
    private String resolveDescription(Spot s) {
        if (s.getDescription() != null && !s.getDescription().isBlank()) {
            return s.getDescription();
        }
        return spotDetailRepository.findBySpotId(s.getId())
                .map(SpotDetail::getOverview)
                .filter(o -> o != null && !o.isBlank())
                .orElse(s.getDescription());
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
            eventPublisher.publishEvent(new EngagementChangedEvent(spotId));

            // 3) 좋아요 50개 단위 마일스톤 알림 (본인 글에 본인이 좋아요를 누른 경우는 제외)
            User author = spot.getUser();
            if (author != null && !author.getId().equals(current.getId())) {
                NotificationFactory.likeMilestone(author, spot.getLikeCount(), spotId)
                        .ifPresent(notificationService::send);
            }
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
                    eventPublisher.publishEvent(new EngagementChangedEvent(spotId));
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
        ImageValidator.validate(f, "이미지 파일이 비어있습니다.");
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
        s.setTitle(req.getTitle());
        s.setUserCreated(true);
        s.setIsDeleted(false);
        applyTheme(s, req.getThemeId());
        applyTags(s, req.getTag1(), req.getTag2(), req.getTag3());
        return spotRepository.save(s).getId();
    }

    private void applyBasics(Spot s, SpotUpdateRequest req) {
        s.setName(req.getName());
        s.setTitle(req.getTitle());
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

    // 이미지(최대 3장)를 순차 업로드하면 각 S3 PUT의 네트워크 왕복이 그대로 더해져서 글쓰기가
    // 느려진다 - 병렬로 보내고 다 끝나길 기다린다. join() 전까지는 요청이 안 끝났으니 MultipartFile이
    // 백업하는 임시 리소스도 아직 유효하다.
    private List<String> uploadAll(Long spotId, List<MultipartFile> files) {
        List<CompletableFuture<String>> uploads = files.stream()
                .map(f -> {
                    validateImage(f);
                    String key = "spot-images/" + spotId + "/" + UUID.randomUUID() + "-" + f.getOriginalFilename();
                    return CompletableFuture.supplyAsync(() -> putS3(f, key, metadataOf(f)));
                })
                .toList();
        return uploads.stream().map(CompletableFuture::join).toList();
    }

    // 마이페이지 관련 메서드
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<SpotResponse> getMyPosts(Pageable pageable, String sort) {
        User user = securityUtil.getAuthenticatedUser();
        Page<Spot> spots;
        
        // 정렬 기준에 따라 다른 쿼리 사용
        switch (sort != null ? sort.toLowerCase() : "latest") {
            case "views":
                spots = spotRepository.findByUserIdOrderByViewCountDesc(user.getId(), pageable);
                break;
            case "comments":
                // 댓글 많은 순은 메모리에서 정렬 (표시만)
                spots = spotRepository.findByUserIdOrderByCreatedAtDescForCommentSort(user.getId(), pageable);
                // TODO: 댓글 많은 순 정렬 구현 (현재는 최신순으로 반환)
                break;
            case "latest":
            default:
                spots = spotRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
                break;
        }
        
        // 현재 사용자가 좋아요한 스팟 ID 목록 조회 (배치 처리)
        List<Long> spotIds = spots.getContent().stream().map(Spot::getId).collect(Collectors.toList());
        Set<Long> likedSpotIds = new HashSet<>();
        if (!spotIds.isEmpty()) {
            likedSpotIds = new HashSet<>(likeRepository.findLikedTargetIds(
                    user.getId(), spotIds, Like.TargetType.SPOT));
        }
        
        final Set<Long> finalLikedSpotIds = likedSpotIds;
        Map<Long, Double> scores = engagementScores(spots.getContent());
        Map<Long, Integer> commentCounts = commentCounts(spots.getContent());
        return spots.map(spot -> SpotResponse.fromEntity(
                spot,
                spot.getLikeCount(),
                finalLikedSpotIds.contains(spot.getId()),
                likesUntilPromotion(spot, scores),
                commentCounts.getOrDefault(spot.getId(), 0)
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
        
        // 모든 스팟에 좋아요를 눌렀으므로 likedByMe는 항상 true
        Map<Long, Double> scores = engagementScores(likedSpots.getContent());
        Map<Long, Integer> commentCounts = commentCounts(likedSpots.getContent());
        return likedSpots.map(spot -> SpotResponse.fromEntity(
                spot,
                spot.getLikeCount(),
                true,
                likesUntilPromotion(spot, scores),
                commentCounts.getOrDefault(spot.getId(), 0)
        ));
    }

    // Reply 엔티티를 ReplyDTO로 변환
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

    // 상대 시간 계산 (예: "5분 전", "2시간 전", "3일 전")
    private String calculateRelativeTime(LocalDateTime createdAt) {
        if (createdAt == null) {
            return "";
        }
        
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(createdAt, now);
        
        long seconds = duration.getSeconds();
        
        if (seconds < 60) {
            return "방금 전";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + "분 전";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + "시간 전";
        } else {
            long days = seconds / 86400;
            return days + "일 전";
        }
    }
}