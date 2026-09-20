package com.goodda.jejuday.spot.service;

import static org.assertj.core.api.Assertions.assertThat;
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
 * spot_likes 동시 좋아요 중복.
 *
 * <p>likeSpot 이 existsBy → save 의 check-then-insert 인데 (user, target) 유니크 제약이 없어서,
 * 같은 유저의 동시 요청(더블탭/재시도)이 중복 행과 like_count 과다 증가를 만든다.
 */
@SpringBootTest
@ActiveProfiles("it")
@DisplayName("스팟 좋아요 — 동시 요청 중복 방지")
class SpotLikeConcurrencyIT {

    @Autowired private SpotService spotService;
    @Autowired private SpotRepository spotRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationOutboxRepository outboxRepository;

    @MockitoBean private SecurityUtil securityUtil;
    @MockitoBean private FirebaseMessaging firebaseMessaging;
    @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private RedisTemplate<String, String> redisTemplate;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private User liker;
    private User author;

    @BeforeEach
    void setup() {
        likeRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
        spotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();


        liker = newUser("liker");
        author = newUser("author");
        when(securityUtil.getAuthenticatedUser()).thenReturn(liker);
    }

    private User newUser(String name) {
        return userRepository.save(User.builder()
                .platform(Platform.APP).gender(Gender.MALE)
                .email(name + "@test.com").birthYear("1990").nickname(name)
                .language(Language.KOREAN)
                .fcmToken("test-fcm-token-with-enough-length-1234")
                .isNotificationEnabled(true).build());
    }

    private Spot newSpot(String name) {
        return spotRepository.save(new Spot(name, "설명", BigDecimal.valueOf(33.4), BigDecimal.valueOf(126.5), author));
    }

    private void concurrently(int threads, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    task.run();
                } catch (Exception ignored) {
                    // 중복 예외가 나더라도 최종 행 수만 검증한다
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
    @DisplayName("같은 유저가 같은 스팟에 16개 스레드로 동시에 좋아요 → 좋아요 행 1개, like_count 1, 알림 1건")
    void 동시_좋아요는_한_행만_남는다() throws InterruptedException {
        List<Spot> spots = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            spots.add(newSpot("spot-" + i));
        }

        for (Spot spot : spots) {
            concurrently(16, () -> spotService.likeSpot(spot.getId()));
        }

        for (Spot spot : spots) {
            long rows = likeRepository.findAll().stream()
                    .filter(l -> l.getTargetType() == Like.TargetType.SPOT && l.getTargetId().equals(spot.getId()))
                    .count();
            assertThat(rows).as("spotId=%d 좋아요 행 수", spot.getId()).isEqualTo(1);
            assertThat(spotRepository.findById(spot.getId()).orElseThrow().getLikeCount())
                    .as("spotId=%d like_count", spot.getId()).isEqualTo(1);
        }
        assertThat(notificationRepository.count()).as("좋아요 알림(작성자에게 1건씩)").isEqualTo(spots.size());
    }

    @Test
    @DisplayName("같은 스팟의 댓글에 좋아요를 먼저 눌러 둔 유저도 스팟 좋아요가 정상 반영된다 (spot_likes 는 스팟/댓글 공용)")
    void 댓글_좋아요가_스팟_좋아요를_가리지_않는다() {
        Spot spot = newSpot("spot-with-reply-like");
        Like replyLike = new Like(liker, spot, Like.TargetType.REPLY);
        replyLike.setTargetId(987654L); // 댓글 id
        likeRepository.save(replyLike);

        spotService.likeSpot(spot.getId());

        boolean spotLikeExists = likeRepository.existsByUserIdAndTargetTypeAndTargetId(
                liker.getId(), Like.TargetType.SPOT, spot.getId());
        assertThat(spotLikeExists).as("스팟 좋아요 행").isTrue();

        // 취소도 스팟 좋아요만 지워야 한다 (댓글 좋아요는 유지)
        spotService.unlikeSpot(spot.getId());
        assertThat(likeRepository.existsByUserIdAndTargetTypeAndTargetId(
                liker.getId(), Like.TargetType.SPOT, spot.getId())).isFalse();
        assertThat(likeRepository.existsByUserIdAndTargetTypeAndTargetId(
                liker.getId(), Like.TargetType.REPLY, 987654L)).as("댓글 좋아요는 유지").isTrue();
    }

    /** 다른 테스트 클래스의 users/spot 삭제가 FK 로 막히지 않게 이 클래스가 만든 행을 정리한다. */
    @AfterEach
    void cleanUpRows() {
        likeRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
        spotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}
