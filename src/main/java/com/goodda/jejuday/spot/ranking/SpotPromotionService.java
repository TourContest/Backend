package com.goodda.jejuday.spot.ranking;

import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.ENGAGEMENT_KEY;
import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.POST_TO_SPOT_ENGAGEMENT_FLOOR;
import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.RANKING_KEY;
import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.SPOT_TO_CHALLENGE_TOP_K;

import com.goodda.jejuday.notification.service.SpotPromotionNotifier;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.SpotRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 승격 판정 잡. 배치 전량 재계산을 하지 않고 SpotRankingUpdater가 증분 갱신해둔
 * ENGAGEMENT_KEY ZSet을 읽어서 판정만 한다 — 가볍게 유지.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotPromotionService {

    private final SpotRepository spotRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final SpotTypeTransitioner transitioner;
    private final SpotPromotionNotifier promotionNotifier;

    private static final int CANDIDATE_WINDOW_DAYS = 14;

    @Scheduled(cron = "0 0 * * * *") // 매시간 정각 실행
    @SchedulerLock(name = "spotPromotion", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void promoteSpotsPeriodically() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(CANDIDATE_WINDOW_DAYS);

        // 두 후보 목록을 승격 이전에 먼저 스냅샷 — POST→SPOT 승격이 같은 회차 안에서 곧바로
        // SPOT→CHALLENGE 후보로 잡혀 연쇄 승격되는 것을 방지 (승격은 회차당 한 단계씩)
        List<Spot> postCandidates = spotRepository.findPromotionCandidatePosts(cutoffDate);
        List<Spot> spotCandidates = spotRepository.findPromotionCandidateSpots(cutoffDate);

        int promotedToSpot = promotePosts(postCandidates);
        int promotedToChallenge = promoteTopSpots(spotCandidates);

        log.info("스팟 승격 프로세스 완료: POST->SPOT={}, SPOT->CHALLENGE={}", promotedToSpot, promotedToChallenge);
    }

    private int promotePosts(List<Spot> candidates) {
        int promoted = 0;
        for (Spot spot : candidates) {
            Double engagement = engagementScoreOf(spot.getId());
            if (engagement != null && engagement >= POST_TO_SPOT_ENGAGEMENT_FLOOR
                    && transitioner.transition(spot.getId(), Spot.SpotType.POST, Spot.SpotType.SPOT)) {
                promotionNotifier.sendSpotPromotionNotification(spot);
                promoted++;
            }
        }
        return promoted;
    }

    private int promoteTopSpots(List<Spot> candidates) {
        if (candidates.isEmpty()) {
            return 0;
        }

        List<Spot> topSpots = candidates.stream()
                .sorted(Comparator.comparingDouble((Spot s) -> engagementScoreOrZero(s.getId())).reversed())
                .limit(SPOT_TO_CHALLENGE_TOP_K)
                .toList();

        int promoted = 0;
        for (Spot spot : topSpots) {
            if (transitioner.transition(spot.getId(), Spot.SpotType.SPOT, Spot.SpotType.CHALLENGE)) {
                promotionNotifier.sendChallengePromotionNotification(spot);
                promoted++;
            }
        }
        return promoted;
    }

    private Double engagementScoreOf(Long spotId) {
        return redisTemplate.opsForZSet().score(ENGAGEMENT_KEY, "community:" + spotId);
    }

    private double engagementScoreOrZero(Long spotId) {
        Double score = engagementScoreOf(spotId);
        return score != null ? score : 0.0;
    }

    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시 실행
    @SchedulerLock(name = "spotRankingCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void cleanUpOldRankingData() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(CANDIDATE_WINDOW_DAYS);
        List<Long> staleSpotIds = spotRepository.findSpotIdsCreatedBefore(cutoffDate);
        if (staleSpotIds.isEmpty()) {
            log.info("오래된 랭킹 데이터 없음");
            return;
        }

        Object[] staleMembers = staleSpotIds.stream()
                .map(id -> (Object) ("community:" + id))
                .toArray();

        redisTemplate.opsForZSet().remove(RANKING_KEY, staleMembers);
        redisTemplate.opsForZSet().remove(ENGAGEMENT_KEY, staleMembers);

        log.info("오래된 랭킹 데이터 정리 완료: {}건", staleMembers.length);
    }
}
