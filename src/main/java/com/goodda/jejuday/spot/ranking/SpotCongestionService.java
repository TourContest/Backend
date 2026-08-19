package com.goodda.jejuday.spot.ranking;

import com.goodda.jejuday.spot.entity.SpotCongestion;
import com.goodda.jejuday.spot.repository.ChallengeParticipationRepository;
import com.goodda.jejuday.spot.repository.SpotCongestionRepository;
import com.goodda.jejuday.spot.repository.SpotViewLogRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 혼잡도 공공API가 아직 없어서, 자체 데이터(최근 조회수 + 방문 인증 건수)로 혼잡도를 근사 계산한다.
 * 방문 인증(ChallengeParticipation)이 단순 조회보다 실제 방문을 의미하는 강한 신호라 가중치를 더 준다.
 * 나중에 실제 공공데이터 API(한국관광공사 방문자 빅데이터 등)를 붙이게 되면, 이 클래스만 그 응답을
 * 0~1 스케일로 변환해 저장하도록 교체하면 된다 (SpotRecommendationServiceImpl 등 소비 측은 변경 불필요).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotCongestionService {

    private static final int RECENT_HOURS = 24;
    private static final long PARTICIPATION_WEIGHT = 3; // 방문 인증 1건 = 조회 3건에 준하는 신호로 취급
    private static final String SOURCE = "internal_calc";

    private final SpotViewLogRepository spotViewLogRepository;
    private final ChallengeParticipationRepository challengeParticipationRepository;
    private final SpotCongestionRepository spotCongestionRepository;

    @Scheduled(cron = "0 0 * * * *") // 매시간 정각 실행
    @SchedulerLock(name = "spotCongestionRecalculation", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void recalculatePeriodically() {
        int updated = recalculateAll();
        log.info("스팟 혼잡도 재계산 완료: {}건", updated);
    }

    @Transactional
    public int recalculateAll() {
        LocalDateTime since = LocalDateTime.now().minusHours(RECENT_HOURS);
        LocalDate today = LocalDate.now();

        Map<Long, Long> rawScores = new HashMap<>();
        toCountMap(spotViewLogRepository.countRecentViewsGroupedBySpot(since))
                .forEach((spotId, count) -> rawScores.merge(spotId, count, Long::sum));
        toCountMap(challengeParticipationRepository.countRecentParticipationsGroupedByChallenge(since))
                .forEach((spotId, count) -> rawScores.merge(spotId, count * PARTICIPATION_WEIGHT, Long::sum));

        if (rawScores.isEmpty()) {
            return 0;
        }

        long max = rawScores.values().stream().mapToLong(Long::longValue).max().orElse(0);
        if (max == 0) {
            return 0;
        }

        for (Map.Entry<Long, Long> entry : rawScores.entrySet()) {
            double score = Math.min(1.0, entry.getValue() / (double) max);
            upsert(entry.getKey(), today, score);
        }
        return rawScores.size();
    }

    private void upsert(Long spotId, LocalDate date, double score) {
        SpotCongestion congestion = spotCongestionRepository.findBySpotIdAndCongestionDate(spotId, date)
                .orElseGet(SpotCongestion::new);
        congestion.setSpotId(spotId);
        congestion.setCongestionDate(date);
        congestion.setInternalScore(score);
        Double external = congestion.getExternalScore();
        congestion.setCongestionScore(external == null ? score : 0.7 * external + 0.3 * score);
        congestion.setSource(external == null ? SOURCE : "kto_forecast+internal");
        congestion.setUpdatedAt(LocalDateTime.now());
        spotCongestionRepository.save(congestion);
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }
}
