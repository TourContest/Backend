package com.goodda.jejuday.spot.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.repository.SpotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NationwideCommunitySeedRunnerTest {

    @Test
    void 수도권은100개씩_그외지역은20개씩_고정된시드를생성한다() {
        List<NationwideCommunitySeedRunner.PostSeed> first = NationwideCommunitySeedRunner.generateSeeds();
        List<NationwideCommunitySeedRunner.PostSeed> second = NationwideCommunitySeedRunner.generateSeeds();

        assertThat(first).hasSize(580).containsExactlyElementsOf(second);
        assertThat(first.stream().map(NationwideCommunitySeedRunner.PostSeed::region).distinct()).hasSize(17);
        var counts = first.stream().collect(java.util.stream.Collectors.groupingBy(
                NationwideCommunitySeedRunner.PostSeed::region, java.util.stream.Collectors.counting()));
        assertThat(counts).containsEntry("서울", 100L).containsEntry("경기", 100L).containsEntry("인천", 100L);
        assertThat(counts).allSatisfy((region, count) ->
                assertThat(count).isEqualTo(List.of("서울", "경기", "인천").contains(region) ? 100L : 20L));
    }

    @Test
    void 모든좌표와카운트가DB컬럼범위안에있다() {
        assertThat(NationwideCommunitySeedRunner.generateSeeds()).allSatisfy(seed -> {
            assertThat(seed.latitude().scale()).isEqualTo(6);
            assertThat(seed.longitude().scale()).isEqualTo(6);
            assertThat(seed.latitude().doubleValue()).isBetween(33.0, 38.5);
            assertThat(seed.longitude().doubleValue()).isBetween(126.0, 130.0);
            assertThat(seed.likeCount()).isBetween(3, 150);
            assertThat(seed.viewCount()).isBetween(30, 1500);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void 커뮤니티와지도스팟을각각580개씩저장한다() {
        SpotRepository spotRepository = mock(SpotRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(spotRepository.existsByExternalPlaceId(any())).thenReturn(false);
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(new User())));
        NationwideCommunitySeedRunner runner = new NationwideCommunitySeedRunner(spotRepository, userRepository);

        runner.seed();

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(spotRepository).saveAll(captor.capture());
        List<Spot> saved = (List<Spot>) captor.getValue();
        assertThat(saved).hasSize(1160);
        assertThat(saved).filteredOn(spot -> spot.getType() == Spot.SpotType.POST && spot.isUserCreated()).hasSize(580);
        assertThat(saved).filteredOn(spot -> spot.getType() == Spot.SpotType.SPOT && !spot.isUserCreated()).hasSize(580);
    }
}
