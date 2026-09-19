package com.goodda.jejuday.spot.ranking;

import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.ENGAGEMENT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.notification.service.SpotPromotionNotifier;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 승격(transition)과 승격 알림 적재의 원자성.
 *
 * <p>transition() 이 자체 트랜잭션으로 커밋된 뒤 알림을 따로 적재하면, 둘 사이에서 프로세스가 죽었을 때
 * "승격은 됐는데 알림은 영영 안 가는" 상태가 남는다 (POST→SPOT 은 from 조건이 이미 깨져 다시 판정되지 않음).
 * 여기서는 알림 적재 지점에 {@link Error} 를 던져 그 사이의 강제 종료를 모사한다 (실제 kill -9 는 아니고
 * "적재가 끝나기 전에 스레드가 끝난다"는 결과가 같은 fault injection).
 */
@SpringBootTest
@ActiveProfiles("it")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("스팟 승격 — 승격과 알림 적재의 원자성")
class SpotPromotionAtomicityIT {

    @Autowired private com.goodda.jejuday.spot.ranking.SpotPromotionService spotPromotionService;
    @Autowired private SpotRepository spotRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private NotificationRepository notificationRepository;

    @MockitoSpyBean private SpotPromotionNotifier promotionNotifier;

    @MockitoBean private FirebaseMessaging firebaseMessaging;
    @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private RedisTemplate<String, String> redisTemplate;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private ZSetOperations<String, String> zSetOps;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
        spotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> ops = mock(ZSetOperations.class);
        zSetOps = ops;
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    }

    private Spot newPostWithEngagement(double engagement) {
        User user = userRepository.save(User.builder()
                .platform(Platform.APP)
                .gender(Gender.MALE)
                .email("atomicity@test.com")
                .birthYear("1990")
                .nickname("atomicity-tester")
                .language(Language.KOREAN)
                .fcmToken("test-fcm-token-with-enough-length-1234")
                .isNotificationEnabled(true)
                .build());
        Spot spot = spotRepository.save(new Spot(
                "테스트 스팟", "설명", BigDecimal.valueOf(33.4), BigDecimal.valueOf(126.5), user));
        when(zSetOps.score(eq(ENGAGEMENT_KEY), eq("community:" + spot.getId()))).thenReturn(engagement);
        return spot;
    }

    private long promotionNotifications(Spot spot) {
        String key = "spot-promote:" + spot.getId();
        return notificationRepository.findAll().stream().filter(n -> key.equals(n.getContextKey())).count();
    }

    @Test
    @DisplayName("전이 커밋 후 알림 적재 전에 종료돼도 '승격됨 ⇔ 알림 있음' 이 유지되고, 다음 회차에 함께 복구된다")
    void 승격과_알림은_함께_반영되거나_함께_취소된다() {
        Spot spot = newPostWithEngagement(21.0);
        doThrow(new Error("simulated kill -9 between transition and notification"))
                .when(promotionNotifier).sendSpotPromotionNotification(any());

        // 1회차: 알림 적재 지점에서 스레드가 죽는다
        assertThatThrownBy(() -> spotPromotionService.promoteAllExisting()).isInstanceOf(Error.class);

        Spot afterCrash = spotRepository.findById(spot.getId()).orElseThrow();
        boolean promoted = afterCrash.getType() == Spot.SpotType.SPOT;
        assertThat(promoted)
                .as("승격 여부(%s)와 알림 존재 여부(%d건)가 일치해야 한다 — 승격은 됐는데 알림이 없으면 유실",
                        afterCrash.getType(), promotionNotifications(spot))
                .isEqualTo(promotionNotifications(spot) > 0);

        // 2회차: 장애 제거 후 다시 실행 — 승격과 알림이 모두 1회씩 반영돼야 한다
        reset(promotionNotifier);
        spotPromotionService.promoteAllExisting();

        assertThat(spotRepository.findById(spot.getId()).orElseThrow().getType()).isEqualTo(Spot.SpotType.SPOT);
        assertThat(promotionNotifications(spot)).as("복구 후 승격 알림(인박스)").isEqualTo(1);
        long outbox = outboxRepository.findAll().stream()
                .filter(o -> ("spot-promote:" + spot.getId()).equals(o.getDedupKey())).count();
        assertThat(outbox).as("복구 후 승격 알림(outbox)").isEqualTo(1);
    }

    @Test
    @DisplayName("SPOT→CHALLENGE 승격도 알림 적재 실패 시 함께 취소된다")
    void 챌린지_승격도_알림과_원자적이다() {
        Spot spot = newPostWithEngagement(50.0);
        // 이미 SPOT 인 스팟으로 만들어 두고 챌린지 후보가 되게 한다
        spot.setType(Spot.SpotType.SPOT);
        spotRepository.save(spot);
        doThrow(new Error("simulated kill -9"))
                .when(promotionNotifier).sendChallengePromotionNotification(any());

        assertThatThrownBy(() -> spotPromotionService.promoteAllExisting()).isInstanceOf(Error.class);

        Spot afterCrash = spotRepository.findById(spot.getId()).orElseThrow();
        String key = "challenge-promote:" + spot.getId();
        long notifs = notificationRepository.findAll().stream().filter(n -> key.equals(n.getContextKey())).count();
        assertThat(afterCrash.getType() == Spot.SpotType.CHALLENGE)
                .as("챌린지 승격 여부(%s)와 알림(%d건)이 일치해야 한다", afterCrash.getType(), notifs)
                .isEqualTo(notifs > 0);
    }
}
