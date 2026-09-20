package com.goodda.jejuday.spot.ranking;

import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.POST_TO_SPOT_ENGAGEMENT_FLOOR;
import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.SPOT_TO_CHALLENGE_TOP_K;

import com.goodda.jejuday.notification.service.SpotPromotionNotifier;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 승격 판정 잡. engagement 는 Redis 가 아니라 DB 실측으로 계산한다:
 * <pre>engagement = spot.like_count × 3 + 삭제되지 않은 최상위 댓글 수</pre>
 * 후보 전체의 댓글 수를 한 번의 배치 GROUP BY 로 구하고(reply.content_id 인덱스), POST→SPOT 전이는
 * "판정 포함 조건부 UPDATE" 한 문장으로 한다(판정 직전 재확인, 부당 승격 방지).
 * Redis 가 죽거나 비워져도 판정 결과는 바뀌지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotPromotionService {

    private static final int CANDIDATE_WINDOW_DAYS = 14;
    private static final int IN_CLAUSE_CHUNK = 1000;

    private final SpotRepository spotRepository;
    private final ReplyRepository replyRepository;
    private final SpotScoreCalculator scoreCalculator;
    private final SpotTypeTransitioner transitioner;
    private final SpotPromotionNotifier promotionNotifier;

    @Scheduled(cron = "0 0 * * * *") // 매시간 정각 실행
    @SchedulerLock(name = "spotPromotion", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void promoteSpotsPeriodically() {
        runPromotion(LocalDateTime.now().minusDays(CANDIDATE_WINDOW_DAYS));
    }

    /**
     * 관리자용 수동 승격 — 생성일 제한(CANDIDATE_WINDOW_DAYS) 없이 기존 POST/SPOT 전체를
     * 대상으로 승격 조건을 판정한다. 정기 배치와 판정 로직(임계값/상위 K)은 동일하다.
     */
    public PromotionResult promoteAllExisting() {
        PromotionResult result = runPromotion(LocalDateTime.of(2000, 1, 1, 0, 0));
        log.info("관리자 수동 승격 완료: POST->SPOT={}, SPOT->CHALLENGE={}",
                result.promotedToSpot(), result.promotedToChallenge());
        return result;
    }

    private PromotionResult runPromotion(LocalDateTime cutoffDate) {
        // 두 후보 목록을 승격 이전에 먼저 스냅샷 — POST→SPOT 승격이 같은 회차 안에서 곧바로
        // SPOT→CHALLENGE 후보로 잡혀 연쇄 승격되는 것을 방지 (승격은 회차당 한 단계씩)
        List<Spot> postCandidates = spotRepository.findPromotionCandidatePosts(cutoffDate);
        List<Spot> spotCandidates = spotRepository.findPromotionCandidateSpots(cutoffDate);

        int promotedToSpot = promotePosts(postCandidates, engagementOf(postCandidates));
        int promotedToChallenge = promoteTopSpots(spotCandidates, engagementOf(spotCandidates));

        log.info("스팟 승격 프로세스 완료: POST->SPOT={}, SPOT->CHALLENGE={}", promotedToSpot, promotedToChallenge);
        return new PromotionResult(promotedToSpot, promotedToChallenge);
    }

    public record PromotionResult(int promotedToSpot, int promotedToChallenge) {}

    /** 후보 전체의 engagement 를 DB 실측으로 계산: like_count(비정규화, 원자적 증감) + 활성 최상위 댓글 수(배치 GROUP BY). */
    private Map<Long, Integer> engagementOf(List<Spot> spots) {
        Map<Long, Integer> replies = new HashMap<>();
        for (int i = 0; i < spots.size(); i += IN_CLAUSE_CHUNK) {
            List<Long> ids = spots.subList(i, Math.min(i + IN_CLAUSE_CHUNK, spots.size())).stream().map(Spot::getId).toList();
            for (Object[] row : replyRepository.countActiveGroupByContentIds(ids, 0)) {
                replies.put((Long) row[0], ((Long) row[1]).intValue());
            }
        }
        Map<Long, Integer> out = new HashMap<>();
        for (Spot s : spots) {
            int likes = s.getLikeCount() == null ? 0 : s.getLikeCount();
            out.put(s.getId(), scoreCalculator.engagementScore(likes, replies.getOrDefault(s.getId(), 0)));
        }
        return out;
    }

    private int promotePosts(List<Spot> candidates, Map<Long, Integer> engagement) {
        int promoted = 0;
        for (Spot spot : candidates) {
            // 사전 필터(메모리). 최종 판정은 전이 UPDATE 안에서 DB 가 다시 한다
            if (engagement.getOrDefault(spot.getId(), 0) >= POST_TO_SPOT_ENGAGEMENT_FLOOR
                    && promoteOne(spot, Spot.SpotType.POST, Spot.SpotType.SPOT, POST_TO_SPOT_ENGAGEMENT_FLOOR,
                            promotionNotifier::sendSpotPromotionNotification)) {
                promoted++;
            }
        }
        return promoted;
    }

    private int promoteTopSpots(List<Spot> candidates, Map<Long, Integer> engagement) {
        if (candidates.isEmpty()) {
            return 0;
        }

        List<Spot> topSpots = candidates.stream()
                .sorted(Comparator.comparingInt((Spot s) -> engagement.getOrDefault(s.getId(), 0)).reversed())
                .limit(SPOT_TO_CHALLENGE_TOP_K)
                .toList();

        int promoted = 0;
        for (Spot spot : topSpots) {
            if (promoteOne(spot, Spot.SpotType.SPOT, Spot.SpotType.CHALLENGE, null,
                    promotionNotifier::sendChallengePromotionNotification)) {
                promoted++;
            }
        }
        return promoted;
    }

    /**
     * 스팟 1건의 전이 + 알림 적재(같은 트랜잭션). 알림 적재가 예외로 실패하면 전이도 롤백되므로
     * 그 스팟만 건너뛰고(다음 회차에 재시도) 나머지 후보는 계속 처리한다.
     */
    private boolean promoteOne(Spot spot, Spot.SpotType from, Spot.SpotType to, Integer minEngagement, Consumer<Spot> notifier) {
        try {
            return transitioner.transitionAndNotify(spot, from, to, minEngagement, notifier);
        } catch (RuntimeException e) {
            log.error("스팟 승격 실패(전이/알림 롤백, 다음 회차 재시도): spotId={}, {}→{}", spot.getId(), from, to, e);
            return false;
        }
    }
}
