package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.goodda.jejuday.spot.repository.SpotViewLogRepository;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpotPromotionService {

    private final SpotRepository spotRepository;
    private final LikeRepository likeRepository;
    private final ReplyRepository replyRepository;
    private final SpotViewLogRepository viewLogRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final double SPOT_THRESHOLD = 8.0;
    private static final double CHALLENGE_THRESHOLD = 11.0;
    private static final LocalDateTime BASE_DATE = LocalDateTime.of(2020, 1, 1, 0, 0);

    @Scheduled(cron = "0 0 */6 * * *") // 매 6시간마다 실행
    @Transactional
    public void promoteSpotsPeriodically() {
        List<Spot> spots = spotRepository.findAll();

        for (Spot spot : spots) {
            if (Boolean.TRUE.equals(spot.getIsDeleted())) {
                continue;
            }

            double score = calculateRedditScore(spot);
            storeRankingInRedis(spot.getId(), score);
            evaluatePromotion(spot, score);
        }
    }

    private double calculateRedditScore(Spot spot) {
        Long spotId = spot.getId();

        int likeCount = likeRepository.countByTargetIdAndTargetType(spotId, Like.TargetType.SPOT);
        int replyCount = replyRepository.findByContentIdAndDepthOrderByCreatedAtDesc(spotId, 0).size();
        int viewCount = spot.getViewCount();
        int certifyCount = 0; // 향후 인증샷 구현 시 확장 가능

        int rawScore = (likeCount * 2) + (replyCount * 3) + viewCount + (certifyCount * 10);
        double order = Math.log10(Math.max(rawScore, 1));
        long seconds = Duration.between(BASE_DATE, spot.getCreatedAt()).getSeconds();

        return order + seconds / 45000.0;
    }

    private void storeRankingInRedis(Long spotId, double score) {
        redisTemplate.opsForZSet().add("community:ranking", "community:" + spotId, score);
    }

    private void evaluatePromotion(Spot spot, double score) {
        Spot.SpotType currentType = spot.getType();

        if (currentType == Spot.SpotType.POST && score >= SPOT_THRESHOLD) {
            promoteToSpot(spot);
        } else if (currentType == Spot.SpotType.SPOT && score >= CHALLENGE_THRESHOLD) {
            promoteToChallenge(spot);
        }
    }

    private void promoteToSpot(Spot spot) {
        spot.setType(Spot.SpotType.SPOT);
        spotRepository.save(spot);

        sendPromotionNotification(
                spot.getUser(),
                "🎉 당신의 게시글 '" + spot.getName() + "'이 제주 스팟으로 선정되었어요!",
                NotificationType.POPULARITY,
                "spot-promote:" + spot.getId()
        );
    }

    private void promoteToChallenge(Spot spot) {
        spot.setType(Spot.SpotType.CHALLENGE);
        spotRepository.save(spot);

        sendPromotionNotification(
                spot.getUser(),
                "🏆 '" + spot.getName() + "'이 챌린저 스팟으로 선정되었어요! 포인트를 모아보세요!",
                NotificationType.CHALLENGE,
                "challenge-promote:" + spot.getId()
        );
    }

    private void sendPromotionNotification(User user, String message, NotificationType type, String contextKey) {
        notificationService.sendNotificationInternal(
                user,
                message,
                type,
                contextKey,
                user.getFcmToken()
        );
    }
}
