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
        notificationService.send(NotificationFactory.promotion(
                user,
                String.format("당신의 게시글 '%s'이 제주 스팟으로 선정되었어요!", spot.getName()),
                NotificationType.POPULARITY,
                "spot-promote:" + spot.getId()
        ));
        log.info("스팟 승격 알림 전송 완료: userId={}, spotId={}", user.getId(), spot.getId());
    }

    public void sendChallengePromotionNotification(Spot spot) {
        User user = spot.getUser();
        notificationService.send(NotificationFactory.promotion(
                user,
                String.format("'%s'이 챌린저 스팟으로 선정되었어요! 포인트를 모아보세요!", spot.getName()),
                NotificationType.CHALLENGE,
                "challenge-promote:" + spot.getId()
        ));
        log.info("챌린지 승격 알림 전송 완료: userId={}, spotId={}", user.getId(), spot.getId());
    }
}
