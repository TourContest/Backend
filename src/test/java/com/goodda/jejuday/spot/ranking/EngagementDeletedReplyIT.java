package com.goodda.jejuday.spot.ranking;

import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.ENGAGEMENT_KEY;
import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.RANKING_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.spot.entity.Reply;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.LikeRepository;
import com.goodda.jejuday.spot.repository.ReplyRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 삭제된 댓글(is_deleted=true)은 engagement(승격 판정) 점수에서 빠져야 한다.
 * 삭제(soft delete)만 하고 카운트 쿼리가 is_deleted 를 보지 않으면, 지운 댓글이 승격 점수를 계속 채운다.
 */
@SpringBootTest
@ActiveProfiles("it")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("engagement 점수 — 삭제된 댓글 제외")
class EngagementDeletedReplyIT {

    @Autowired private SpotRankingUpdater rankingUpdater;
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
    private Spot spot;

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

        User user = userRepository.save(User.builder()
                .platform(Platform.APP).gender(Gender.MALE)
                .email("deleted-reply@test.com").birthYear("1990").nickname("deleted-reply")
                .language(Language.KOREAN).build());
        spot = spotRepository.save(new Spot("스팟", "설명", BigDecimal.valueOf(33.4), BigDecimal.valueOf(126.5), user));

        User u = user;
        // 최상위 댓글 2개(1개 삭제) + 삭제 안 된 답글 1개(depth 1 은 점수에 안 들어감)
        Reply active = reply(u, 0, false);
        reply(u, 0, true);
        Reply child = reply(u, 1, false);
        child.setParentReply(active);
        replyRepository.save(child);
    }

    private Reply reply(User user, int depth, boolean deleted) {
        Reply r = new Reply();
        r.setContentId(spot.getId());
        r.setUser(user);
        r.setText("t");
        r.setDepth(depth);
        r.setIsDeleted(deleted);
        return replyRepository.save(r);
    }

    @Test
    @DisplayName("이벤트 소비자(SpotRankingUpdater)는 삭제되지 않은 최상위 댓글만 센다: 기대 engagement = 1")
    void 갱신_소비자는_삭제된_댓글을_세지_않는다() {
        rankingUpdater.on(new EngagementChangedEvent(spot.getId()));

        ArgumentCaptor<Double> score = ArgumentCaptor.forClass(Double.class);
        verify(zSetOps).add(eq(ENGAGEMENT_KEY), eq("community:" + spot.getId()), score.capture());
        assertThat(score.getValue()).as("삭제된 댓글 1개가 점수에 포함되면 2.0").isEqualTo(1.0);
    }

    @Test
    @DisplayName("보정 배치도 같은 정의를 쓴다: 삭제 댓글이 섞인 옛 점수(2)를 드리프트로 보고 1로 고친다")
    void 보정_배치도_삭제된_댓글을_세지_않는다() {
        String member = "community:" + spot.getId();
        when(zSetOps.score(ENGAGEMENT_KEY, member)).thenReturn(2.0); // 삭제 댓글을 포함해 계산된 옛 점수
        when(zSetOps.score(RANKING_KEY, member)).thenReturn(0.0);

        reconciliationJob.reconcile();

        verify(zSetOps).add(eq(ENGAGEMENT_KEY), eq(member), eq(1.0));
    }

    @Test
    @DisplayName("갱신 소비자가 쓴 값을 보정 배치가 그대로 인정한다 (두 경로의 카운트 정의 일치)")
    void 갱신_소비자와_보정_배치의_정의가_일치한다() {
        String member = "community:" + spot.getId();
        rankingUpdater.on(new EngagementChangedEvent(spot.getId()));
        ArgumentCaptor<Double> hot = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> eng = ArgumentCaptor.forClass(Double.class);
        verify(zSetOps).add(eq(RANKING_KEY), eq(member), hot.capture());
        verify(zSetOps).add(eq(ENGAGEMENT_KEY), eq(member), eng.capture());
        org.mockito.Mockito.clearInvocations(zSetOps);
        when(zSetOps.score(ENGAGEMENT_KEY, member)).thenReturn(eng.getValue());
        when(zSetOps.score(RANKING_KEY, member)).thenReturn(hot.getValue());

        reconciliationJob.reconcile();

        verify(zSetOps, never()).add(eq(ENGAGEMENT_KEY), eq(member), anyDouble());
        verify(zSetOps, never()).add(eq(RANKING_KEY), eq(member), anyDouble());
    }
}
