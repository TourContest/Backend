package com.goodda.jejuday.spot.ranking;

import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.ENGAGEMENT_KEY;
import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.RANKING_KEY;

import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요/댓글 변경 이벤트를 커밋 후에만 반영해 랭킹 ZSet을 증분 갱신한다.
 * 카운터 INCR 대신 이벤트 시점에 해당 스팟만 DB에서 재조회 — 카운터 드리프트를 원천 차단.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpotRankingUpdater {

    private final SpotRepository spotRepository;
    private final LikeRepository likeRepository;
    private final ReplyRepository replyRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final SpotScoreCalculator scoreCalculator;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(EngagementChangedEvent event) {
        Long spotId = event.spotId();
        spotRepository.findById(spotId).ifPresent(spot -> updateRanking(spot));
    }

    private void updateRanking(Spot spot) {
        Long spotId = spot.getId();
        int likes = likeRepository.countByTargetIdAndTargetType(spotId, Like.TargetType.SPOT);
        int replies = replyRepository.countByContentIdAndDepth(spotId, 0);
        int views = spot.getViewCount();

        int engagement = scoreCalculator.engagementScore(likes, replies);
        double hot = scoreCalculator.hotScore(likes, replies, views, spot.getCreatedAt());

        String member = "community:" + spotId;
        redisTemplate.opsForZSet().add(RANKING_KEY, member, hot);
        redisTemplate.opsForZSet().add(ENGAGEMENT_KEY, member, engagement);

        log.debug("랭킹 갱신: spotId={}, 좋아요={}, 댓글={}, 조회={}, engagement={}, hot={}",
                spotId, likes, replies, views, engagement, hot);
    }
}
