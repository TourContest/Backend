package com.goodda.jejuday.spot.ranking;

import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.ENGAGEMENT_KEY;
import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.RANKING_KEY;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Reply;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * Step 4 — 보정 배치: 이벤트가 유실되어 ZSet이 실제 DB 상태와 어긋난 상황(드리프트)을
 * 보정 배치가 감지하고 수정하는지 검증한다.
 *
 * <p>테스트 메서드마다 컨텍스트를 새로 띄운다 — Mockito 모킹된 RedisTemplate이 클래스 인스턴스
 * 간 공유되면서 stubbing이 미묘하게 얽히는 것을 방지하기 위함(관찰된 flaky 실패의 원인).
 */
@SpringBootTest
@ActiveProfiles("it")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("스팟 인게이지먼트 보정 배치 — 드리프트 감지/수정")
class SpotEngagementReconciliationJobIT {

    @Autowired private SpotEngagementReconciliationJob reconciliationJob;
    @Autowired private SpotRepository spotRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LikeRepository likeRepository;
    @Autowired private ReplyRepository replyRepository;

    @MockitoBean private FirebaseMessaging firebaseMessaging;
    @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private RedisTemplate<String, String> redisTemplate;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private ZSetOperations<String, String> zSetOps;

    @BeforeEach
    void setup() {
        replyRepository.deleteAllInBatch();
        likeRepository.deleteAllInBatch();
        spotRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> ops = mock(ZSetOperations.class);
        zSetOps = ops;
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
    }

    @Test
    @DisplayName("이벤트 유실로 ZSet에 반영 안 된 좋아요/댓글을 보정 배치가 실제값으로 수정한다")
    void 이벤트_유실을_보정_배치가_감지하고_수정한다() {
        User author = userRepository.save(newUser("author@test.com", "author"));
        User liker1 = userRepository.save(newUser("liker1@test.com", "liker1"));
        User liker2 = userRepository.save(newUser("liker2@test.com", "liker2"));

        Spot spot = spotRepository.save(new Spot(
                "드리프트 테스트 스팟", "설명", BigDecimal.valueOf(33.4), BigDecimal.valueOf(126.5), author));

        // 이벤트 발행 없이 직접 DB에만 반영 — "이벤트 유실" 상황 재현
        likeRepository.save(new Like(liker1, spot, Like.TargetType.SPOT));
        likeRepository.save(new Like(liker2, spot, Like.TargetType.SPOT));

        Reply reply = new Reply();
        reply.setContentId(spot.getId());
        reply.setUser(author);
        reply.setText("댓글");
        reply.setDepth(0);
        reply.setCreatedAt(LocalDateTime.now());
        replyRepository.save(reply);

        String member = "community:" + spot.getId();
        // ZSet에는 반영되지 않은 상태(이벤트 유실) — score가 없음
        when(zSetOps.score(eq(ENGAGEMENT_KEY), anyString())).thenReturn(null);
        when(zSetOps.score(eq(RANKING_KEY), anyString())).thenReturn(null);

        reconciliationJob.reconcile();

        // 실제 DB 값: 좋아요 2개, 댓글 1개 → engagementScore = 2*3 + 1*1 = 7
        verify(zSetOps).add(eq(ENGAGEMENT_KEY), eq(member), eq(7.0));

        Spot reloaded = spotRepository.findById(spot.getId()).orElseThrow();
        SpotScoreCalculator calculator = new SpotScoreCalculator();
        double expectedHot = calculator.hotScore(2, 1, reloaded.getViewCount(), reloaded.getCreatedAt());
        verify(zSetOps).add(eq(RANKING_KEY), eq(member), eq(expectedHot));
    }

    @Test
    @DisplayName("ZSet 값이 이미 실제 DB 상태와 일치하면 드리프트 수정을 하지 않는다")
    void 드리프트가_없으면_수정하지_않는다() {
        User author = userRepository.save(newUser("no-drift@test.com", "nodrift"));
        Spot spot = spotRepository.save(new Spot(
                "정상 스팟", "설명", BigDecimal.valueOf(33.4), BigDecimal.valueOf(126.5), author));

        String member = "community:" + spot.getId();
        SpotScoreCalculator calculator = new SpotScoreCalculator();
        int engagement = calculator.engagementScore(0, 0);
        double hot = calculator.hotScore(0, 0, spot.getViewCount(), spot.getCreatedAt());

        when(zSetOps.score(eq(ENGAGEMENT_KEY), eq(member))).thenReturn((double) engagement);
        when(zSetOps.score(eq(RANKING_KEY), eq(member))).thenReturn(hot);

        reconciliationJob.reconcile();

        org.mockito.Mockito.verify(zSetOps, org.mockito.Mockito.never())
                .add(eq(ENGAGEMENT_KEY), eq(member), org.mockito.ArgumentMatchers.anyDouble());
    }

    private User newUser(String email, String nickname) {
        return User.builder()
                .platform(Platform.APP)
                .gender(Gender.MALE)
                .email(email)
                .birthYear("1990")
                .nickname(nickname)
                .language(Language.KOREAN)
                .isNotificationEnabled(true)
                .build();
    }
}
