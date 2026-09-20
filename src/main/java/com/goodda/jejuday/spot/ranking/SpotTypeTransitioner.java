package com.goodda.jejuday.spot.ranking;

import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.entity.Spot.SpotType;
import com.goodda.jejuday.spot.repository.SpotRepository;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스팟 단위로 별도 트랜잭션에서 상태를 전이한다 (배치 전체를 하나의 트랜잭션으로 묶지 않기 위해 분리 —
 * 부분 실패가 배치 전체를 롤백하지 않도록 함. 같은 클래스 내 self-invocation으로는
 * @Transactional 프록시가 걸리지 않으므로 별도 빈으로 분리).
 */
@Component
@RequiredArgsConstructor
public class SpotTypeTransitioner {

    private final SpotRepository spotRepository;

    /** @return 전이가 실제로 일어났으면 true (affected rows == 1) — 중복 실행에도 정확히 1회만 true */
    @Transactional
    public boolean transition(Long spotId, SpotType from, SpotType to) {
        return spotRepository.transition(spotId, from, to) == 1;
    }

    /**
     * 전이와 알림 적재를 한 트랜잭션으로 묶는다.
     *
     * <p>전이만 먼저 커밋하고 알림을 따로 넣으면 둘 사이에서 프로세스가 죽었을 때 "승격은 됐는데 알림이 없는"
     * 상태가 남고, from 조건이 이미 깨져 다시 판정되지 않아 알림이 영영 유실된다. 여기서는 알림 적재
     * (NotificationService.send 는 REQUIRED 로 이 트랜잭션에 합류)가 실패하거나 도중에 종료되면
     * 전이도 함께 롤백돼 다음 회차에 다시 판정된다.
     *
     * @return 전이가 일어났고 알림까지 적재했으면 true. 조건부 UPDATE 영향 행이 0이면(이미 승격됨) 알림 없이 false
     */
    @Transactional
    public boolean transitionAndNotify(Spot spot, SpotType from, SpotType to, Consumer<Spot> notifier) {
        return transitionAndNotify(spot, from, to, null, notifier);
    }

    /**
     * @param minEngagement null 이 아니면 전이 UPDATE 한 문장에 "DB 실측 engagement ≥ minEngagement" 판정을 포함한다
     *                      (판정 직전 재확인 + 판정~전이 사이 틈 제거).
     */
    @Transactional
    public boolean transitionAndNotify(Spot spot, SpotType from, SpotType to, Integer minEngagement, Consumer<Spot> notifier) {
        int updated = (minEngagement == null)
                ? spotRepository.transition(spot.getId(), from, to)
                : spotRepository.transitionIfEngagementAtLeast(
                        spot.getId(), from.name(), to.name(), minEngagement, SpotRankingConstants.LIKE_WEIGHT);
        if (updated != 1) {
            return false;
        }
        notifier.accept(spot);
        return true;
    }
}
