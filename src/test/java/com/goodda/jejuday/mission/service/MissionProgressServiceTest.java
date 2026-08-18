package com.goodda.jejuday.mission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.mission.entity.MissionStep;
import com.goodda.jejuday.mission.entity.MissionTheme;
import com.goodda.jejuday.mission.entity.UserMissionCompletion;
import com.goodda.jejuday.mission.repository.MissionStepRepository;
import com.goodda.jejuday.mission.repository.UserMissionCompletionRepository;
import com.goodda.jejuday.mission.repository.UserMissionStepRepository;
import com.goodda.jejuday.notification.service.NotificationService;
import com.goodda.jejuday.pay.entity.LedgerReason;
import com.goodda.jejuday.pay.service.PointLedgerService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MissionProgressServiceTest {

    @Test
    void completingMissionAwardsAdditionalOneThousandHallabong() {
        MissionStepRepository stepRepository = mock(MissionStepRepository.class);
        UserMissionStepRepository userStepRepository = mock(UserMissionStepRepository.class);
        UserMissionCompletionRepository completionRepository = mock(UserMissionCompletionRepository.class);
        PointLedgerService pointLedgerService = mock(PointLedgerService.class);
        NotificationService notificationService = mock(NotificationService.class);
        MissionProgressService service = new MissionProgressService(
                stepRepository, userStepRepository, completionRepository,
                pointLedgerService, notificationService
        );

        User user = User.builder().id(1L).build();
        MissionTheme theme = new MissionTheme();
        theme.setId(10L);
        theme.setTitle("오름 5선");
        theme.setTotalSteps(1);
        theme.setCompletionRewardHallabong(500);
        MissionStep step = new MissionStep();
        step.setId(20L);
        step.setMissionTheme(theme);

        when(stepRepository.findBySpot_Id(30L)).thenReturn(List.of(step));
        when(userStepRepository.existsByUser_IdAndMissionStep_Id(1L, 20L)).thenReturn(false);
        when(completionRepository.existsByUser_IdAndMissionTheme_Id(1L, 10L)).thenReturn(false);
        when(userStepRepository.countByUser_IdAndMissionStep_MissionTheme_Id(1L, 10L)).thenReturn(1L);

        service.recordSpotVisit(user, 30L);

        verify(pointLedgerService).record(
                eq(1L), eq(1_000), eq(LedgerReason.MISSION_COMPLETE), eq(10L), eq("1:MISSION:10"));
        ArgumentCaptor<UserMissionCompletion> captor = ArgumentCaptor.forClass(UserMissionCompletion.class);
        verify(completionRepository).save(captor.capture());
        assertThat(captor.getValue().getHallabongAwarded()).isEqualTo(1_000);
    }
}
