package com.goodda.jejuday.steps.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.notification.dto.NotificationRequest;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.notification.service.NotificationService;
import com.goodda.jejuday.steps.dto.StepRequestDto;
import com.goodda.jejuday.steps.entity.StepDaily;
import com.goodda.jejuday.steps.repository.StepDailyRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 걸음수 목표(2만보) 알림 경로: recordSteps 가 하루에 send 를 몇 번 호출하는가.
 * 순차 업로드에서는 "이전 < 2만 && 현재 ≥ 2만" 조건으로 정확히 1번. 같은 유저의 동시 업로드에서는
 * todayRecord.addSteps 가 read-modify-write 라 여러 번 호출될 수 있다 (알림함/푸시는 dedup_key 로 1건 보장).
 */
@SpringBootTest
@ActiveProfiles("it")
@DisplayName("걸음수 목표 알림 — send 호출 횟수와 알림함 1건 보장")
class StepGoalNotificationIT {

    @Autowired private StepService stepService;
    @Autowired private StepDailyRepository stepDailyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationOutboxRepository outboxRepository;

    @MockitoSpyBean private NotificationService notificationService;
    @MockitoBean private FirebaseMessaging firebaseMessaging;
    @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private RedisTemplate<String, String> redisTemplate;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private User user;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
        stepDailyRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        user = userRepository.save(User.builder()
                .platform(Platform.APP).gender(Gender.MALE)
                .email("stepgoal@test.com").birthYear("1990").nickname("stepgoal")
                .language(Language.KOREAN).fcmToken("test-fcm-token-with-enough-length-1234")
                .isNotificationEnabled(true).build());
        Mockito.reset(notificationService);
    }

    private void seedToday(long totalSteps) {
        stepDailyRepository.save(StepDaily.builder().user(user).date(LocalDate.now())
                .totalSteps(totalSteps).convertedPoints(0).startBonusApplied(true).build());
    }

    private int stepSendCalls() {
        ArgumentCaptor<NotificationRequest> cap = ArgumentCaptor.forClass(NotificationRequest.class);
        Mockito.verify(notificationService, Mockito.atLeast(0)).send(cap.capture());
        return (int) cap.getAllValues().stream().filter(r -> r.type() == NotificationType.STEP).count();
    }

    private long stepInboxRows() {
        return notificationRepository.findAll().stream().filter(n -> n.getType() == NotificationType.STEP).count();
    }

    @Test
    @DisplayName("순차 업로드: 2만보를 넘는 순간에만 send 1번, 이후 업로드는 호출 없음")
    void 순차_업로드는_하루_한_번_호출한다() {
        seedToday(0);
        for (int i = 0; i < 6; i++) {
            stepService.recordSteps(user.getId(), new StepRequestDto(5_000));   // 5천 × 6 = 3만 (4번째에 2만 도달)
        }
        assertThat(stepSendCalls()).as("send 호출 횟수").isEqualTo(1);
        assertThat(stepInboxRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 유저의 동시 업로드: send 호출 횟수를 기록하고, 호출이 여러 번이어도 알림함은 1건")
    void 동시_업로드에서도_알림함은_한_건이다() throws InterruptedException {
        seedToday(19_900);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failed = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    stepService.recordSteps(user.getId(), new StepRequestDto(200));   // 각자 넘김: 19,900 + 200 ≥ 20,000
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        go.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        long total = stepDailyRepository.findAll().get(0).getTotalSteps();
        int calls = stepSendCalls();
        System.out.println("[측정] 동시 업로드 " + threads + "스레드: send 호출=" + calls + "회, 알림함=" + stepInboxRows()
                + "건, 실패=" + failed.get() + ", 최종 걸음수=" + total + " (기대 " + (19_900 + 200L * (threads - failed.get())) + ")");
        assertThat(stepInboxRows()).as("알림함 행 수").isEqualTo(1);
    }
}
