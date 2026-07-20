package com.goodda.jejuday.spot.ranking;

import com.goodda.jejuday.spot.entity.Spot.SpotType;
import com.goodda.jejuday.spot.repository.SpotRepository;
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
}
