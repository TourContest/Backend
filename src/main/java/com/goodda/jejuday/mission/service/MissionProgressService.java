package com.goodda.jejuday.mission.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.mission.dto.CompletedMissionResponse;
import com.goodda.jejuday.mission.entity.MissionStep;
import com.goodda.jejuday.mission.entity.MissionTheme;
import com.goodda.jejuday.mission.entity.UserMissionCompletion;
import com.goodda.jejuday.mission.entity.UserMissionStep;
import com.goodda.jejuday.mission.repository.MissionStepRepository;
import com.goodda.jejuday.mission.repository.UserMissionCompletionRepository;
import com.goodda.jejuday.mission.repository.UserMissionStepRepository;
import com.goodda.jejuday.notification.service.NotificationFactory;
import com.goodda.jejuday.notification.service.NotificationService;
import com.goodda.jejuday.pay.entity.LedgerReason;
import com.goodda.jejuday.pay.service.PointLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 챌린지 완료(ChallengeActionService.complete()) 시 호출되어, 방금 완료한 spotId가 속한
 * 미션 스텝 진행도를 갱신하고, 테마 전체를 완주했으면 완주 보상을 지급한다.
 */
@Service
@RequiredArgsConstructor
public class MissionProgressService {

    private final MissionStepRepository stepRepository;
    private final UserMissionStepRepository userStepRepository;
    private final UserMissionCompletionRepository completionRepository;
    private final PointLedgerService pointLedgerService;
    private final NotificationService notificationService;

    @Transactional
    public List<CompletedMissionResponse> recordSpotVisit(User user, Long spotId) {
        List<MissionStep> steps = stepRepository.findBySpot_Id(spotId);
        if (steps.isEmpty()) {
            return List.of();
        }

        List<CompletedMissionResponse> newlyCompleted = new ArrayList<>();

        for (MissionStep step : steps) {
            // 1) 스텝 완료 기록 (멱등 — 이미 있으면 skip)
            if (!userStepRepository.existsByUser_IdAndMissionStep_Id(user.getId(), step.getId())) {
                UserMissionStep ums = new UserMissionStep();
                ums.setUser(user);
                ums.setMissionStep(step);
                userStepRepository.save(ums);
            }

            MissionTheme theme = step.getMissionTheme();

            // 2) 이미 완주 보상을 받았으면 스킵 (중복 지급 방지)
            if (completionRepository.existsByUser_IdAndMissionTheme_Id(user.getId(), theme.getId())) {
                continue;
            }

            // 3) 테마의 전체 스텝을 다 채웠는지 확인
            long completedCount = userStepRepository
                    .countByUser_IdAndMissionStep_MissionTheme_Id(user.getId(), theme.getId());
            if (completedCount < theme.getTotalSteps()) {
                continue;
            }

            // 4) 완주 보상 지급 — 멱등 키: userId:MISSION:missionThemeId
            int reward = theme.getCompletionRewardHallabong();
            if (reward > 0) {
                String idemKey = user.getId() + ":MISSION:" + theme.getId();
                pointLedgerService.record(user.getId(), reward, LedgerReason.MISSION_COMPLETE, theme.getId(), idemKey);
            }

            UserMissionCompletion completion = new UserMissionCompletion();
            completion.setUser(user);
            completion.setMissionTheme(theme);
            completion.setHallabongAwarded(reward);
            completionRepository.save(completion);

            notificationService.send(NotificationFactory.missionComplete(
                    user, String.format("'%s' 미션을 완주해서 한라봉 %,d개를 받았어요!", theme.getTitle(), reward),
                    theme.getId()));

            newlyCompleted.add(new CompletedMissionResponse(theme.getId(), theme.getTitle()));
        }

        return newlyCompleted;
    }
}
