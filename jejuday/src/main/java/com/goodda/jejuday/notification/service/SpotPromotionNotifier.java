package com.goodda.jejuday.notification.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.spot.entity.Spot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpotPromotionNotifier {

    private final NotificationService notificationService;

    public void sendSpotPromotionNotification(Spot spot) {
        User user = spot.getUser();
        String message = createSpotPromotionMessage(spot.getName());
        String contextKey = createSpotPromotionContextKey(spot.getId());

        sendPromotionNotification(user, message, NotificationType.POPULARITY, contextKey);

        log.info("스팟 승격 알림 전송 완료: 사용자={}, 스팟={}", user.getId(), spot.getId());
    }

    public void sendChallengePromotionNotification(Spot spot) {
        User user = spot.getUser();
        String message = createChallengePromotionMessage(spot.getName());
        String contextKey = createChallengePromotionContextKey(spot.getId());

        sendPromotionNotification(user, message, NotificationType.CHALLENGE, contextKey);

        log.info("챌린지 승격 알림 전송 완료: 사용자={}, 스팟={}", user.getId(), spot.getId());
    }

    private String createSpotPromotionMessage(String spotName) {
        return String.format("당신의 게시글 '%s'이 제주 스팟으로 선정되었어요!", spotName);
    }

    private String createChallengePromotionMessage(String spotName) {
        return String.format("'%s'이 챌린저 스팟으로 선정되었어요! 포인트를 모아보세요!", spotName);
    }

    private String createSpotPromotionContextKey(Long spotId) {
        return "spot-promote:" + spotId;
    }

    private String createChallengePromotionContextKey(Long spotId) {
        return "challenge-promote:" + spotId;
    }

    private void sendPromotionNotification(User user, String message, NotificationType type, String contextKey) {
        try {
            notificationService.sendNotificationInternal(
                    user,
                    message,
                    type,
                    contextKey,
                    user.getFcmToken()
            );
        } catch (Exception e) {
            log.error("승격 알림 전송 실패: 사용자={}, 타입={}, 에러={}",
                    user.getId(), type, e.getMessage(), e);
        }
    }
}