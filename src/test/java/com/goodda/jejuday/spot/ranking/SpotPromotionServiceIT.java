package com.goodda.jejuday.spot.ranking;

import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.ENGAGEMENT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

/**
 * Step 4 — 판정 정확성 & 멱등성: engagementScore가 하한선을 넘는 POST가 2스레드 동시 판정 실행에도
 * 전이 1회 + 알림 outbox 1건만 발생하는지 검증한다 (원자적 UPDATE + ShedLock 이중 안전장치).
 *
 * <p>테스트 메서드마다 컨텍스트를 새로 띄운다 — 모킹된 RedisTemplate/ZSetOperations 공유로 인한
 * stubbing 얽힘을 방지하기 위함.
 */
@SpringBootTest
@ActiveProfiles("it")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("스팟 승격 판정 — 정확성/멱등성 통합 테스트")
class SpotPromotionServiceIT {

    @Autowired private SpotPromotionService spotPromotionService;
    @Autowired private SpotRepository spotRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private ReplyRepository replyRepository;

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

    @Test
    @DisplayName("engagementScore가 하한선 이상인 POST는 2스레드 동시 판정에도 전이 1회, 알림 1건만 발생한다")
    void 동시_판정_실행에도_승격은_한_번만_일어난다() throws InterruptedException {
        User user = userRepository.save(User.builder()
                .platform(Platform.APP)
                .gender(Gender.MALE)
                .email("promotion-test@test.com")
                .birthYear("1990")
                .nickname("promotion-tester")
                .language(Language.KOREAN)
                .fcmToken("test-fcm-token-with-enough-length-1234")
                .isNotificationEnabled(true)
                .build());

        Spot spot = spotRepository.save(new Spot(
                "테스트 스팟", "설명", BigDecimal.valueOf(33.4), BigDecimal.valueOf(126.5), user));

        String member = "community:" + spot.getId();
        when(zSetOps.score(eq(ENGAGEMENT_KEY), eq(member))).thenReturn(21.0); // 하한선(20) 이상

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    spotPromotionService.promoteSpotsPeriodically();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        Spot reloaded = spotRepository.findById(spot.getId()).orElseThrow();
        assertThat(reloaded.getType()).isEqualTo(Spot.SpotType.SPOT);

        long outboxCount = outboxRepository.findAll().stream()
                .filter(o -> ("spot-promote:" + spot.getId()).equals(o.getDedupKey()))
                .count();
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    @DisplayName("engagementScore가 하한선 미만인 POST는 승격되지 않는다")
    void 하한선_미만은_승격되지_않는다() {
        User user = userRepository.save(User.builder()
                .platform(Platform.APP)
                .gender(Gender.MALE)
                .email("no-promotion-test@test.com")
                .birthYear("1990")
                .nickname("no-promotion-tester")
                .language(Language.KOREAN)
                .fcmToken("test-fcm-token-with-enough-length-5678")
                .isNotificationEnabled(true)
                .build());

        Spot spot = spotRepository.save(new Spot(
                "인게이지먼트 0 스팟", "설명", BigDecimal.valueOf(33.4), BigDecimal.valueOf(126.5), user));

        when(zSetOps.score(eq(ENGAGEMENT_KEY), anyString())).thenReturn(null); // 이벤트 발생 전(=0)

        spotPromotionService.promoteSpotsPeriodically();

        Spot reloaded = spotRepository.findById(spot.getId()).orElseThrow();
        assertThat(reloaded.getType()).isEqualTo(Spot.SpotType.POST);
    }
}
