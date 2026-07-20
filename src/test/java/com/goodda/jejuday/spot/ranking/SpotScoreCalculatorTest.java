package com.goodda.jejuday.spot.ranking;

import static com.goodda.jejuday.spot.ranking.SpotRankingConstants.POST_TO_SPOT_ENGAGEMENT_FLOOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpotScoreCalculatorTest {

    private final SpotScoreCalculator calculator = new SpotScoreCalculator();

    @Test
    @DisplayName("좋아요/댓글/조회수가 전부 0인 글은 승격 하한선을 넘지 못한다 (결함 수정 확인)")
    void 인게이지먼트_0인_글은_하한선을_넘지_못한다() {
        int engagement = calculator.engagementScore(0, 0);

        assertThat(engagement).isZero();
        assertThat(engagement).isLessThan(POST_TO_SPOT_ENGAGEMENT_FLOOR);
    }

    @Test
    @DisplayName("판정용 engagementScore는 조회수를 반영하지 않는다 — 조작 가능한 수동 신호이므로 배제")
    void engagementScore는_조회수를_무시한다() {
        int withoutViews = calculator.engagementScore(2, 1);
        // views 인자 자체가 없으므로, 좋아요/댓글이 같으면 조회수가 얼마든 결과가 같다는 것이 곧 배제 증명
        int sameLikesReplies = calculator.engagementScore(2, 1);

        assertThat(withoutViews).isEqualTo(sameLikesReplies);
        assertThat(withoutViews).isEqualTo(2 * 3 + 1 * 1); // LIKE_WEIGHT=3, REPLY_WEIGHT=1
    }

    @Test
    @DisplayName("정렬용 hotScore는 조회수가 늘수록(다른 조건 동일) 커진다")
    void hotScore는_조회수를_반영한다() {
        LocalDateTime createdAt = LocalDateTime.now();

        double lowViews = calculator.hotScore(1, 0, 0, createdAt);
        double highViews = calculator.hotScore(1, 0, 100, createdAt);

        assertThat(highViews).isGreaterThan(lowViews);
    }

    @Test
    @DisplayName("hotScore는 같은 인게이지먼트라도 더 최근 글일수록 값이 크다")
    void hotScore는_최신_글일수록_크다() {
        LocalDateTime older = LocalDateTime.now().minusDays(10);
        LocalDateTime newer = LocalDateTime.now();

        double olderScore = calculator.hotScore(5, 2, 10, older);
        double newerScore = calculator.hotScore(5, 2, 10, newer);

        assertThat(newerScore).isGreaterThan(olderScore);
    }

    @Test
    @DisplayName("좋아요 7개(하한선 20 이상)면 승격 가능선에 도달한다")
    void 좋아요_7개면_하한선에_도달한다() {
        int engagement = calculator.engagementScore(7, 0);

        assertThat(engagement).isGreaterThanOrEqualTo(POST_TO_SPOT_ENGAGEMENT_FLOOR);
    }
}
