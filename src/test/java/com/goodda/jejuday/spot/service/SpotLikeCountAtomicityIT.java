package com.goodda.jejuday.spot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * spot.like_count 증감의 원자성.
 * likeSpot/unlikeSpot 이 spot.setLikeCount(getLikeCount() ± 1) 후 save 하는 read-modify-write 라서,
 * 서로 다른 유저의 동시 요청이 서로의 증감을 덮어쓴다(lost update). 또 Spot 에 @DynamicUpdate 가 없어
 * 다른 경로(조회수 증가 등)의 save 가 로드 시점의 오래된 like_count 까지 함께 덮어쓴다.
 */
@SpringBootTest
@ActiveProfiles("it")
@DisplayName("스팟 like_count — 원자적 증감")
class SpotLikeCountAtomicityIT {

    private static final int N = 16;

    @Autowired private SpotService spotService;
    @Autowired private SpotRepository spotRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationOutboxRepository outboxRepository;
    @Autowired private TransactionTemplate tx;

    @MockitoBean private SecurityUtil securityUtil;
    @MockitoBean private FirebaseMessaging firebaseMessaging;
    @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private RedisTemplate<String, String> redisTemplate;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private final ThreadLocal<User> current = new ThreadLocal<>();
    private final List<User> likers = new ArrayList<>();
    private User author;

    @BeforeEach
    void setup() {
        likeRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
        spotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> ops = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(ops);
        when(securityUtil.getAuthenticatedUser()).thenAnswer(inv -> current.get());

        author = user("author");
        likers.clear();
        for (int i = 0; i < N; i++) {
            likers.add(user("liker" + i));
        }
    }

    private User user(String name) {
        return userRepository.save(User.builder().platform(Platform.APP).gender(Gender.MALE)
                .email(name + "@test.com").birthYear("1990").nickname(name).language(Language.KOREAN)
                .fcmToken("test-fcm-token-with-enough-length-1234").isNotificationEnabled(true).build());
    }

    private Spot spot() {
        return spotRepository.save(new Spot("스팟", "설명", BigDecimal.valueOf(33.4), BigDecimal.valueOf(126.5), author));
    }

    private int likeCountOf(Spot s) {
        return spotRepository.findById(s.getId()).orElseThrow().getLikeCount();
    }

    private void runConcurrently(List<Runnable> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks.size());
        for (Runnable t : tasks) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    t.run();
                } catch (Exception ignored) {
                    // 예외 여부와 무관하게 최종 상태만 검증
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        go.countDown();
        done.await(60, TimeUnit.SECONDS);
        pool.shutdown();
    }

    @Test
    @DisplayName("서로 다른 16명이 동시에 좋아요 → like_count = 좋아요 행 수 = 16")
    void 동시_좋아요는_증가분을_잃지_않는다() throws InterruptedException {
        Spot spot = spot();
        List<Runnable> tasks = new ArrayList<>();
        for (User u : likers) {
            tasks.add(() -> {
                current.set(u);
                spotService.likeSpot(spot.getId());
            });
        }
        runConcurrently(tasks);

        long rows = likeRepository.findAll().stream().filter(l -> l.getTargetType() == Like.TargetType.SPOT).count();
        assertThat(rows).as("좋아요 행").isEqualTo(N);
        assertThat(likeCountOf(spot)).as("like_count (lost update 시 16 미만)").isEqualTo(N);
    }

    @Test
    @DisplayName("16명이 동시에 좋아요 취소 → like_count 0")
    void 동시_취소는_감소분을_잃지_않는다() throws InterruptedException {
        Spot spot = spot();
        for (User u : likers) {
            likeRepository.insertIgnore(u.getId(), spot.getId(), Like.TargetType.SPOT.name(), spot.getId());
        }
        tx.executeWithoutResult(s -> {
            Spot managed = spotRepository.findById(spot.getId()).orElseThrow();
            managed.setLikeCount(N);
        });

        List<Runnable> tasks = new ArrayList<>();
        for (User u : likers) {
            tasks.add(() -> {
                current.set(u);
                spotService.unlikeSpot(spot.getId());
            });
        }
        runConcurrently(tasks);

        assertThat(likeRepository.findAll()).as("남은 좋아요 행").isEmpty();
        assertThat(likeCountOf(spot)).as("like_count (lost update 시 0 초과)").isZero();
    }

    @Test
    @DisplayName("같은 유저의 동시 이중 취소 → 한 번만 차감되고 음수가 되지 않는다")
    void 동시_이중_취소는_한_번만_차감한다() throws InterruptedException {
        Spot spot = spot();
        // 다른 유저 3명이 이미 좋아요 + 이 유저 1명 → like_count 4
        List<User> others = likers.subList(0, 3);
        for (User u : others) {
            likeRepository.insertIgnore(u.getId(), spot.getId(), Like.TargetType.SPOT.name(), spot.getId());
        }
        User me = likers.get(5);
        likeRepository.insertIgnore(me.getId(), spot.getId(), Like.TargetType.SPOT.name(), spot.getId());
        tx.executeWithoutResult(s -> spotRepository.findById(spot.getId()).orElseThrow().setLikeCount(4));

        AtomicInteger errors = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                current.set(me);
                try {
                    spotService.unlikeSpot(spot.getId());
                } catch (RuntimeException e) {
                    errors.incrementAndGet();
                    throw e;
                }
            });
        }
        runConcurrently(tasks);

        assertThat(likeCountOf(spot)).as("4 - 1(내 취소 1회) = 3, 이중 차감이면 3 미만").isEqualTo(3);
        assertThat(errors.get()).as("중복 취소가 예외로 새어 나오면 안 된다").isZero();
    }

    @Test
    @DisplayName("다른 트랜잭션의 좋아요가 커밋된 뒤, 오래 열려 있던 트랜잭션이 조회수만 바꿔 저장해도 like_count 를 덮어쓰지 않는다")
    void 다른_경로의_save가_like_count를_덮어쓰지_않는다() {
        Spot spot = spot();
        current.set(likers.get(0));

        tx.executeWithoutResult(status -> {
            Spot stale = spotRepository.findById(spot.getId()).orElseThrow();   // like_count=0 로 로드
            // 다른 트랜잭션(별도 스레드)에서 좋아요가 커밋된다
            Thread t = new Thread(() -> {
                current.set(likers.get(1));
                spotService.likeSpot(spot.getId());
            });
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stale.setViewCount(stale.getViewCount() + 1);   // SpotServiceImpl.getSpot 의 viewCount++ 와 같은 형태
            spotRepository.save(stale);
        });

        Spot after = spotRepository.findById(spot.getId()).orElseThrow();
        assertThat(after.getViewCount()).isEqualTo(1);
        assertThat(after.getLikeCount()).as("좋아요 1건이 커밋됐으므로 1 (통째 UPDATE 면 0 으로 덮어씀)").isEqualTo(1);
    }
}
