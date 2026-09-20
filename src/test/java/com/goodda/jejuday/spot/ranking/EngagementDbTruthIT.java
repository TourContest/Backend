package com.goodda.jejuday.spot.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.repository.NotificationRepository;
import com.goodda.jejuday.spot.dto.ReplyRequest;
import com.goodda.jejuday.spot.dto.SpotResponse;
import com.goodda.jejuday.spot.entity.Reply;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.goodda.jejuday.spot.service.SpotCommentService;
import com.goodda.jejuday.spot.service.SpotService;
import com.google.firebase.messaging.FirebaseMessaging;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 옵션 Z: engagement(승격 점수)의 기준을 Redis ZSet 이 아니라 DB 실측으로 옮긴다.
 *   engagement = like_count × 3 + 삭제되지 않은 최상위 댓글 수
 * Redis 가 통째로 죽어 있어도(모든 상호작용이 예외) 승격 판정 / 화면 표시 / 좋아요·댓글이 동작하고,
 * 승격 결과는 ZSet 값과 무관하게 DB 실측과 일치해야 한다.
 */
@SpringBootTest
@ActiveProfiles("it")
@DisplayName("engagement — DB 실측 기준 (Redis 무관)")
class EngagementDbTruthIT {

    @Autowired private SpotPromotionService promotionService;
    @Autowired private SpotService spotService;
    @Autowired private SpotCommentService commentService;
    @Autowired private SpotRepository spotRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private ReplyRepository replyRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationOutboxRepository outboxRepository;

    @MockitoBean private SecurityUtil securityUtil;
    @MockitoBean private FirebaseMessaging firebaseMessaging;
    @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private RedisTemplate<String, String> redisTemplate;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private User author;

    @BeforeEach
    void setup() {
        deleteAll();
        // Redis 는 어떤 상호작용도 예외 — 죽은 Redis 를 흉내
        when(redisTemplate.opsForZSet()).thenThrow(new IllegalStateException("Redis 장애"));
        when(redisTemplate.executePipelined(any(org.springframework.data.redis.core.RedisCallback.class)))
                .thenThrow(new IllegalStateException("Redis 장애"));
        author = userRepository.save(User.builder().platform(Platform.APP).gender(Gender.MALE)
                .email("author@test.com").birthYear("1990").nickname("author").language(Language.KOREAN)
                .fcmToken("test-fcm-token-with-enough-length-1234").isNotificationEnabled(true).build());
        when(securityUtil.getAuthenticatedUser()).thenReturn(author);
    }

    @AfterEach
    void tearDown() {
        deleteAll();
    }

    private void deleteAll() {
        replyRepository.findAll().stream().filter(r -> r.getParentReply() != null).forEach(replyRepository::delete);
        replyRepository.deleteAllInBatch();
        likeRepository.deleteAllInBatch();
        notificationRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
        spotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private Spot post(int likeCount) {
        Spot s = spotRepository.save(new Spot("스팟", "설명", BigDecimal.valueOf(33.4), BigDecimal.valueOf(126.5), author));
        s.setLikeCount(likeCount);
        s.setUserCreated(true);   // 커뮤니티 피드(목록)는 유저 작성 글만 조회
        return spotRepository.save(s);
    }

    private void reply(Spot s, boolean deleted) {
        Reply r = new Reply();
        r.setContentId(s.getId());
        r.setUser(author);
        r.setText("t");
        r.setDepth(0);
        r.setIsDeleted(deleted);
        replyRepository.save(r);
    }

    private Spot.SpotType typeOf(Spot s) {
        return spotRepository.findById(s.getId()).orElseThrow().getType();
    }

    @Test
    @DisplayName("Redis 가 죽어 있어도 DB 실측 engagement ≥ 10 인 POST 는 승격되고 알림도 1건 적재된다")
    void 레디스_없이_승격된다() {
        Spot enough = post(4);                 // 4×3 = 12
        Spot notEnough = post(3);              // 9

        promotionService.promoteAllExisting();

        assertThat(typeOf(enough)).isEqualTo(Spot.SpotType.SPOT);
        assertThat(typeOf(notEnough)).isEqualTo(Spot.SpotType.POST);
        assertThat(notificationRepository.findAll().stream()
                .filter(n -> ("spot-promote:" + enough.getId()).equals(n.getContextKey())).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 3 + 삭제된 댓글 1 = 9 → 승격되지 않는다 (삭제 댓글 제외), 활성 댓글 1개가 더해지면 10 → 승격")
    void 삭제된_댓글은_점수에_들어가지_않는다() {
        Spot onlyDeleted = post(3);
        reply(onlyDeleted, true);
        Spot withActive = post(3);
        reply(withActive, true);
        reply(withActive, false);

        promotionService.promoteAllExisting();

        assertThat(typeOf(onlyDeleted)).as("삭제 댓글을 세면 10 이 돼 잘못 승격").isEqualTo(Spot.SpotType.POST);
        assertThat(typeOf(withActive)).isEqualTo(Spot.SpotType.SPOT);
    }

    @Test
    @DisplayName("SPOT→CHALLENGE 상위 K 선정도 DB 실측 순위를 쓴다")
    void 챌린지_승격은_DB_순위() {
        Spot low = post(1), mid = post(5), high = post(9), higher = post(12);
        for (Spot s : new Spot[] {low, mid, high, higher}) {
            s.setType(Spot.SpotType.SPOT);
            spotRepository.save(s);
        }

        promotionService.promoteAllExisting();

        assertThat(typeOf(higher)).isEqualTo(Spot.SpotType.CHALLENGE);
        assertThat(typeOf(high)).isEqualTo(Spot.SpotType.CHALLENGE);     // 상위 2개 (SPOT_TO_CHALLENGE_TOP_K)
        assertThat(typeOf(mid)).isEqualTo(Spot.SpotType.SPOT);
        assertThat(typeOf(low)).isEqualTo(Spot.SpotType.SPOT);
    }

    @Test
    @DisplayName("목록 API 의 '승격까지 좋아요 N개' 는 Redis 없이 DB 로 계산된다: engagement 4 → ceil((10-4)/3) = 2")
    void 화면_표시는_DB로_계산된다() {
        Spot s = post(1);                      // 3
        reply(s, false);                       // +1 = 4
        reply(s, true);                        // 삭제 댓글은 제외

        var page = spotService.getLatestSpots(PageRequest.of(0, 20), Spot.SpotType.POST);

        SpotResponse r = page.getContent().stream().filter(x -> x.getId().equals(s.getId())).findFirst().orElseThrow();
        assertThat(r.getLikesUntilPromotion()).isEqualTo(2);
    }

    @Test
    @DisplayName("좋아요·댓글 요청 경로에서 Redis 를 전혀 호출하지 않는다")
    void 좋아요_댓글은_레디스를_호출하지_않는다() {
        Spot s = post(0);
        User liker = userRepository.save(User.builder().platform(Platform.APP).gender(Gender.MALE)
                .email("liker@test.com").birthYear("1990").nickname("liker").language(Language.KOREAN).build());
        when(securityUtil.getAuthenticatedUser()).thenReturn(liker);

        spotService.likeSpot(s.getId());
        ReplyRequest req = new ReplyRequest();
        req.setText("댓글");
        commentService.createComment(s.getId(), req);
        spotService.unlikeSpot(s.getId());

        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(stringRedisTemplate);
    }
}
