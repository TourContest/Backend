package com.goodda.jejuday.spot.ranking;

import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.ENGAGEMENT_KEY;
import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.RANKING_KEY;

import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 이벤트 기반 증분 갱신(SpotRankingUpdater)이 유실됐을 가능성(발행 실패, 배포 중 유실 등)을 하루 한 번
 * DB 실측값과 대조해 잡아내는 보정 배치. 이벤트 경로가 정확하면 드리프트는 0건이어야 하며,
 * 드리프트 발생 건수 로그가 곧 "이벤트 경로가 정확하다"는 운영 증거가 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpotEngagementReconciliationJob {

    private static final int WINDOW_DAYS = 14;
    private static final double HOT_SCORE_EPSILON = 1e-6;

    private final SpotRepository spotRepository;
    private final LikeRepository likeRepository;
    private final ReplyRepository replyRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final SpotScoreCalculator scoreCalculator;

    @Scheduled(cron = "0 0 4 * * *") // 매일 새벽 4시 실행
    @SchedulerLock(name = "spotEngagementReconciliation", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void reconcile() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(WINDOW_DAYS);
        List<Spot> activeSpots = spotRepository.findActiveSpotsCreatedAfterWithUser(cutoffDate);
        if (activeSpots.isEmpty()) {
            log.info("보정 배치: 대상 스팟 없음");
            return;
        }

        List<Long> spotIds = activeSpots.stream().map(Spot::getId).toList();
        Map<Long, Long> likeCounts = likeRepository.getLikeCountsForSpots(spotIds);
        Map<Long, Integer> replyCounts = toReplyCountMap(
                replyRepository.countGroupByContentIds(spotIds, 0));

        int driftCount = 0;
        for (Spot spot : activeSpots) {
            int likes = likeCounts.getOrDefault(spot.getId(), 0L).intValue();
            int replies = replyCounts.getOrDefault(spot.getId(), 0);
            int views = spot.getViewCount();

            int actualEngagement = scoreCalculator.engagementScore(likes, replies);
            double actualHot = scoreCalculator.hotScore(likes, replies, views, spot.getCreatedAt());

            if (isDrifted(spot.getId(), actualEngagement, actualHot)) {
                driftCount++;
                String member = "community:" + spot.getId();
                redisTemplate.opsForZSet().add(ENGAGEMENT_KEY, member, actualEngagement);
                redisTemplate.opsForZSet().add(RANKING_KEY, member, actualHot);
                log.warn("랭킹 드리프트 수정: spotId={}, 좋아요={}, 댓글={}, 조회={}, engagement={}, hot={}",
                        spot.getId(), likes, replies, views, actualEngagement, actualHot);
            }
        }

        log.info("보정 배치 완료: 대상={}건, 드리프트 수정={}건", activeSpots.size(), driftCount);
    }

    private boolean isDrifted(Long spotId, int actualEngagement, double actualHot) {
        String member = "community:" + spotId;
        Double storedEngagement = redisTemplate.opsForZSet().score(ENGAGEMENT_KEY, member);
        Double storedHot = redisTemplate.opsForZSet().score(RANKING_KEY, member);

        boolean engagementDrifted = storedEngagement == null || storedEngagement != actualEngagement;
        boolean hotDrifted = storedHot == null || Math.abs(storedHot - actualHot) > HOT_SCORE_EPSILON;
        return engagementDrifted || hotDrifted;
    }

    private Map<Long, Integer> toReplyCountMap(List<Object[]> rows) {
        Map<Long, Integer> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], ((Long) row[1]).intValue());
        }
        return result;
    }
}
