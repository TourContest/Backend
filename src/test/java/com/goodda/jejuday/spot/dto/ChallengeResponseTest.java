package com.goodda.jejuday.spot.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.goodda.jejuday.spot.entity.ChallengeParticipation;
import com.goodda.jejuday.spot.entity.ChallengeParticipation.Status;
import com.goodda.jejuday.spot.entity.Spot;
import org.junit.jupiter.api.Test;

class ChallengeResponseTest {

    @Test
    void challengeResponseUsesDefaultPointWhenPointIsNull() {
        Spot spot = new Spot();

        ChallengeResponse response = ChallengeResponse.of(spot);

        assertThat(response.getPoint()).isEqualTo(300);
    }

    @Test
    void myChallengeResponseUsesDefaultPointWhenPointIsZero() {
        Spot spot = new Spot();
        spot.setPoint(0);
        ChallengeParticipation participation = new ChallengeParticipation();
        participation.setStatus(Status.JOINED);

        MyChallengeResponse response = MyChallengeResponse.of(spot, participation);

        assertThat(response.getPoint()).isEqualTo(300);
    }

    @Test
    void challengeResponseUsesFixedPointEvenWhenSpotHasAnotherPoint() {
        Spot spot = new Spot();
        spot.setPoint(700);

        ChallengeResponse response = ChallengeResponse.of(spot);

        assertThat(response.getPoint()).isEqualTo(300);
    }
}
