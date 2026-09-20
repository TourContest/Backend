package com.goodda.jejuday.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.entity.NotificationEntity;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 알림함(notification_entity) 멱등 알림 중복 방지.
 *
 * <p>걸음수 목표(step-goal:{date})와 승격(spot-promote:/challenge-promote:) 알림은 같은 키로 여러 번 send 가 호출돼도
 * 알림함에 1건만 남아야 한다. 반면 댓글/답글/댓글 좋아요 알림은 같은 context_key 가 의도적으로 반복되므로
 * (한 글에 댓글이 여러 개 달리면 그때마다 알림함에 쌓인다) 유니크를 걸면 안 된다 — 이 동작은 그대로여야 한다.
 */
@SpringBootTest
@ActiveProfiles("it")
@DisplayName("알림함 — 멱등 알림에만 선택적 중복 방지")
class NotificationDedupIT {

    @Autowired private NotificationService notificationService;
    @Autowired private SpotPromotionNotifier promotionNotifier;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SpotRepository spotRepository;

    @MockitoBean private FirebaseMessaging firebaseMessaging;
    @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private RedisTemplate<String, String> redisTemplate;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private User user;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
        spotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        user = userRepository.save(User.builder()
                .platform(Platform.APP).gender(Gender.MALE)
                .email("dedup@test.com").birthYear("1990").nickname("dedup-tester")
                .language(Language.KOREAN)
                .fcmToken("test-fcm-token-with-enough-length-1234")
                .isNotificationEnabled(true).build());
    }

    private long inboxRows(String contextKey) {
        return notificationRepository.findAll().stream().filter(n -> contextKey.equals(n.getContextKey())).count();
    }

    private long outboxRows(String contextKey) {
        return outboxRepository.findAll().stream().filter(o -> contextKey.equals(o.getDedupKey())).count();
    }

    @Test
    @DisplayName("걸음수 목표 알림을 같은 날 여러 번 send 해도 알림함 1건, outbox 1건")
    void 걸음수_목표_알림은_하루_한_건() {
        for (int i = 0; i < 5; i++) {
            notificationService.send(NotificationFactory.step(user, "오늘 목표 2만보 달성!"));
        }
        String key = "step-goal:" + java.time.LocalDate.now();

        assertThat(inboxRows(key)).as("알림함(인박스) 행 수").isEqualTo(1);
        assertThat(outboxRows(key)).as("outbox 행 수").isEqualTo(1);
    }

    @Test
    @DisplayName("승격 알림을 재전달(중복 호출)해도 알림함 1건")
    void 승격_알림은_스팟당_한_건() {
        Spot spot = spotRepository.save(new Spot("스팟", "설명", BigDecimal.valueOf(33.4), BigDecimal.valueOf(126.5), user));

        promotionNotifier.sendSpotPromotionNotification(spot);
        promotionNotifier.sendSpotPromotionNotification(spot);
        promotionNotifier.sendChallengePromotionNotification(spot);
        promotionNotifier.sendChallengePromotionNotification(spot);

        assertThat(inboxRows("spot-promote:" + spot.getId())).isEqualTo(1);
        assertThat(inboxRows("challenge-promote:" + spot.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("동시에 같은 걸음수 목표 알림을 보내도 알림함 1건 (유니크 위반 예외 없이)")
    void 동시_전송도_한_건() throws InterruptedException {
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    notificationService.send(NotificationFactory.step(user, "동시"));
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        go.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(inboxRows("step-goal:" + java.time.LocalDate.now())).isEqualTo(1);
        assertThat(failures.get()).as("유니크 충돌이 예외로 새어 나오면 안 된다").isZero();
    }

    @Test
    @DisplayName("멱등이 아닌 알림(댓글)은 같은 context_key 로 반복돼도 그대로 알림함에 쌓인다 (기존 동작 유지)")
    void 댓글_알림은_반복_적재된다() {
        notificationService.send(NotificationFactory.reply(user, "첫 번째 댓글", 77L));
        notificationService.send(NotificationFactory.reply(user, "두 번째 댓글", 77L));
        notificationService.send(NotificationFactory.commentReply(user, 5L, "첫 답글"));
        notificationService.send(NotificationFactory.commentReply(user, 5L, "두 번째 답글"));

        assertThat(inboxRows("post:77:reply")).isEqualTo(2);
        assertThat(inboxRows("comment:5")).isEqualTo(2);
        // outbox(푸시)는 기존대로 (user,type,dedup_key) 로 1건씩만 — 이 동작도 바꾸지 않는다
        assertThat(outboxRows("post:77:reply")).isEqualTo(1);
    }

    /** 다른 테스트 클래스의 users/spot 삭제가 FK 로 막히지 않게 이 클래스가 만든 행을 정리한다. */
    @AfterEach
    void cleanUpRows() {
        notificationRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
        spotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}
