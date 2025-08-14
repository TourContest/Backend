package com.goodda.jejuday.notification.service;

import static com.goodda.jejuday.notification.util.NotificationConstants.LIKE_WEIGHT;
import static com.goodda.jejuday.notification.util.NotificationConstants.MIN_TIME_WEIGHT;
import static com.goodda.jejuday.notification.util.NotificationConstants.REPLY_WEIGHT;
import static com.goodda.jejuday.notification.util.NotificationConstants.SCORE_CACHE_KEY;
import static com.goodda.jejuday.notification.util.NotificationConstants.SCORE_CACHE_TTL;
import static com.goodda.jejuday.notification.util.NotificationConstants.TIME_DECAY_DAYS;
import static com.goodda.jejuday.notification.util.NotificationConstants.VIEW_WEIGHT;

import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpotScoreCalculator {

    private final LikeRepository likeRepository;
    private final ReplyRepository replyRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public double calculateScore(Spot spot) {
        String cacheKey = String.format(SCORE_CACHE_KEY, spot.getId());

        // 캐시에서 점수 조회
        String cachedScore = redisTemplate.opsForValue().get(cacheKey);
        if (cachedScore != null) {
            try {
                return Double.parseDouble(cachedScore);
            } catch (NumberFormatException e) {
                log.warn("캐시된 점수 파싱 실패: {}", cachedScore);
            }
        }

        // 개별 게시글 기준으로 점수 계산
        double score = computeIndividualSpotScore(spot);

        // 캐시에 저장
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(score), SCORE_CACHE_TTL);

        log.info("스팟 점수 계산 완료: ID={}, 점수={}", spot.getId(), score);
        return score;
    }

    // 개별 게시글 기준 점수 계산 (위치 무관)
    private double computeIndividualSpotScore(Spot spot) {
        // 해당 게시글(spot)만의 인게이지먼트 점수 계산
        int individualEngagementScore = calculateIndividualEngagementScore(spot);
        double timeWeight = calculateTimeWeight(spot.getCreatedAt());
        double redditScore = calculateRedditStyleScore(individualEngagementScore, spot.getCreatedAt());

        log.debug("개별 점수 계산: ID={}, 인게이지먼트={}, 시간가중치={}, 최종점수={}",
                spot.getId(), individualEngagementScore, timeWeight, redditScore * timeWeight);

        return redditScore * timeWeight;
    }

    // 개별 게시글의 인게이지먼트 점수만 계산
    private int calculateIndividualEngagementScore(Spot spot) {
        // 해당 게시글에 대한 좋아요, 댓글, 조회수만 계산
        int likeCount = getIndividualLikeCount(spot.getId());
        int replyCount = getIndividualReplyCount(spot.getId());
        int viewCount = spot.getViewCount(); // 게시글 자체의 조회수

        int score = (replyCount * REPLY_WEIGHT) +
                (likeCount * LIKE_WEIGHT) +
                (viewCount * VIEW_WEIGHT);

        log.debug("개별 인게이지먼트 점수: ID={}, 좋아요={}, 댓글={}, 조회={}, 총점={}",
                spot.getId(), likeCount, replyCount, viewCount, score);

        return score;
    }

    // 특정 게시글에 대한 좋아요 수만 조회
    private int getIndividualLikeCount(Long spotId) {
        String cacheKey = "spot:individual:likes:" + spotId;
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            try {
                return Integer.parseInt(cached);
            } catch (NumberFormatException e) {
                log.warn("캐시된 좋아요 수 파싱 실패: {}", cached);
            }
        }

        // 해당 게시글에 대한 좋아요만 계산
        int count = likeRepository.countByTargetIdAndTargetType(spotId, Like.TargetType.SPOT);
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(count), Duration.ofMinutes(10));

        log.debug("개별 좋아요 수: 게시글={}, 좋아요={}", spotId, count);
        return count;
    }

    // 특정 게시글에 대한 댓글 수만 조회
    private int getIndividualReplyCount(Long spotId) {
        String cacheKey = "spot:individual:replies:" + spotId;
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            try {
                return Integer.parseInt(cached);
            } catch (NumberFormatException e) {
                log.warn("캐시된 댓글 수 파싱 실패: {}", cached);
            }
        }

        // 해당 게시글에 대한 최상위 댓글(depth=0)만 카운트
        int count = replyRepository.findByContentIdAndDepthOrderByCreatedAtDesc(spotId, 0).size();
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(count), Duration.ofMinutes(10));

        log.debug("개별 댓글 수: 게시글={}, 댓글={}", spotId, count);
        return count;
    }

    // 시간 가중치 계산 (기존과 동일)
    private double calculateTimeWeight(LocalDateTime createdAt) {
        long daysSinceCreation = Duration.between(createdAt, LocalDateTime.now()).toDays();

        if (daysSinceCreation <= TIME_DECAY_DAYS) {
            double decayRatio = (double) daysSinceCreation / TIME_DECAY_DAYS;
            return 1.0 - (decayRatio * (1.0 - MIN_TIME_WEIGHT));
        } else {
            return MIN_TIME_WEIGHT;
        }
    }

    // Reddit 스타일 점수 계산 (기존과 동일)
    private double calculateRedditStyleScore(int engagementScore, LocalDateTime createdAt) {
        double order = Math.log10(Math.max(engagementScore, 1));

        LocalDateTime baseDate = LocalDateTime.of(2020, 1, 1, 0, 0);
        long seconds = Duration.between(baseDate, createdAt).getSeconds();
        double timeComponent = seconds / 45000.0;

        return order + timeComponent;
    }

    // 배치 처리 메서드도 개별 기준으로 수정
    public Map<Long, Double> calculateScoresForSpots(List<Spot> spots) {
        return spots.stream()
                .collect(Collectors.toMap(
                        Spot::getId,
                        this::calculateScore // 개별 계산 메서드 사용
                ));
    }

    // 캐시 무효화 메서드 수정
    public void invalidateScoreCache(Long spotId) {
        String scoreKey = String.format(SCORE_CACHE_KEY, spotId);
        String likesKey = "spot:individual:likes:" + spotId;
        String repliesKey = "spot:individual:replies:" + spotId;

        redisTemplate.delete(scoreKey);
        redisTemplate.delete(likesKey);
        redisTemplate.delete(repliesKey);

        log.info("개별 스팟 캐시 삭제 완료: 게시글={}", spotId);
    }
}