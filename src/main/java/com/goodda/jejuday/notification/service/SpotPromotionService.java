package com.goodda.jejuday.notification.service;

import static com.goodda.jejuday.notification.util.NotificationConstants.POST_TO_SPOT_THRESHOLD;
import static com.goodda.jejuday.notification.util.NotificationConstants.PROMOTION_CACHE_KEY;
import static com.goodda.jejuday.notification.util.NotificationConstants.PROMOTION_CACHE_TTL;
import static com.goodda.jejuday.notification.util.NotificationConstants.RANKING_KEY;
import static com.goodda.jejuday.notification.util.NotificationConstants.SPOT_TO_CHALLENGE_PERCENTAGE;

import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotPromotionService {

    private final SpotRepository spotRepository;
    private final LikeRepository likeRepository;
    private final ReplyRepository replyRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;
    private final SpotScoreCalculator scoreCalculator;
    private final SpotPromotionNotifier promotionNotifier;

    @Scheduled(cron = "0 0 18 * * *") // 매 6시간마다 실행
    @Transactional
    public void promoteSpotsPeriodically() {
        log.info("스팟 승격 프로세스 시작");

        List<Spot> activeSpots = getActiveSpotsWithCache();
        Map<Long, Double> scoreMap = calculateScoresForSpots(activeSpots);

        updateRankingInRedis(scoreMap);
        processPromotions(activeSpots, scoreMap);

        log.info("스팟 승격 프로세스 완료. 처리된 스팟 수: {}", activeSpots.size());
    }

    private List<Spot> getActiveSpotsWithCache() {
        // 2주 이전 날짜 계산
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(14);

        // N+1 문제 해결을 위해 사용자 정보와 함께 조회하고, 2주 이내 스팟만 대상으로 함
        return spotRepository.findActiveSpotsCreatedAfterWithUser(cutoffDate);
    }

    private Map<Long, Double> calculateScoresForSpots(List<Spot> spots) {
        // 배치 처리로 성능 개선
        return scoreCalculator.calculateScoresForSpots(spots);
    }

    private void updateRankingInRedis(Map<Long, Double> scoreMap) {
        scoreMap.forEach((spotId, score) -> {
            String member = "community:" + spotId;
            redisTemplate.opsForZSet().add(RANKING_KEY, member, score);
        });
    }

    private void processPromotions(List<Spot> spots, Map<Long, Double> scoreMap) {
        for (Spot spot : spots) {
            if (isPromotionAlreadyExecuted(spot.getId())) {
                continue;
            }

            Double score = scoreMap.get(spot.getId());
            if (score != null) {
                evaluateAndPromote(spot, score, spots);
            }
        }
    }

    private boolean isPromotionAlreadyExecuted(Long spotId) {
        String cacheKey = String.format(PROMOTION_CACHE_KEY, spotId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
    }

    private void evaluateAndPromote(Spot spot, double score, List<Spot> allSpots) {
        Spot.SpotType currentType = spot.getType();

        if (currentType == Spot.SpotType.POST && shouldPromoteToSpot(score)) {
            promoteToSpot(spot);
        } else if (currentType == Spot.SpotType.SPOT && shouldPromoteToChallenge(spot, allSpots)) {
            promoteToChallenge(spot);
        }
    }

    private boolean shouldPromoteToSpot(double score) {
        return score >= POST_TO_SPOT_THRESHOLD;
    }

    private boolean shouldPromoteToChallenge(Spot spot, List<Spot> allSpots) {
        List<Spot> spotTypeSpots = allSpots.stream()
                .filter(s -> s.getType() == Spot.SpotType.SPOT)
                .collect(Collectors.toList());

        if (spotTypeSpots.isEmpty()) {
            return false;
        }

        int topCount = Math.max(1, (int) Math.ceil(spotTypeSpots.size() * SPOT_TO_CHALLENGE_PERCENTAGE));
        topCount = Math.min(topCount, 2);

        // 현재 스팟이 상위 30%에 포함되는지 확인
        List<Spot> topSpots = spotTypeSpots.stream()
                .sorted((s1, s2) -> Double.compare(
                        scoreCalculator.calculateScore(s2),
                        scoreCalculator.calculateScore(s1)
                ))
                .limit(topCount)
                .collect(Collectors.toList());

        return topSpots.contains(spot);
    }

    private void promoteToSpot(Spot spot) {
        spot.setType(Spot.SpotType.SPOT);
        spotRepository.save(spot);

        promotionNotifier.sendSpotPromotionNotification(spot);
        cachePromotionExecution(spot.getId());

        log.info("스팟 승격 완료: {} -> SPOT", spot.getId());
    }

    private void promoteToChallenge(Spot spot) {
        spot.setType(Spot.SpotType.CHALLENGE);
        spotRepository.save(spot);

        promotionNotifier.sendChallengePromotionNotification(spot);
        cachePromotionExecution(spot.getId());

        log.info("챌린지 승격 완료: {} -> CHALLENGE", spot.getId());
    }

    private void cachePromotionExecution(Long spotId) {
        String cacheKey = String.format(PROMOTION_CACHE_KEY, spotId);
        redisTemplate.opsForValue().set(cacheKey, "executed", PROMOTION_CACHE_TTL);
    }

    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시 실행
    public void cleanUpOldRankingData() {
        log.info("오래된 랭킹 데이터 정리 시작");

        Set<String> allMembers = redisTemplate.opsForZSet().range(RANKING_KEY, 0, -1);
        if (allMembers != null) {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(14);

            for (String member : allMembers) {
                String spotIdStr = member.replace("community:", "");
                try {
                    Long spotId = Long.valueOf(spotIdStr);
                    if (isSpotOlderThan(spotId, cutoffDate)) {
                        redisTemplate.opsForZSet().remove(RANKING_KEY, member);
                    }
                } catch (NumberFormatException e) {
                    log.warn("잘못된 형식의 랭킹 멤버: {}", member);
                    redisTemplate.opsForZSet().remove(RANKING_KEY, member);
                }
            }
        }

        log.info("오래된 랭킹 데이터 정리 완료");
    }

    private boolean isSpotOlderThan(Long spotId, LocalDateTime cutoffDate) {
        return spotRepository.findById(spotId)
                .map(spot -> spot.getCreatedAt().isBefore(cutoffDate))
                .orElse(true); // 존재하지 않는 스팟은 제거
    }
}