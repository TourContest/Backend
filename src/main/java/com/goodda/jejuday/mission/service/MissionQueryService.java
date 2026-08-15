package com.goodda.jejuday.mission.service;

import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.mission.dto.MissionStepResponse;
import com.goodda.jejuday.mission.dto.MissionThemeResponse;
import com.goodda.jejuday.mission.entity.MissionStep;
import com.goodda.jejuday.mission.entity.MissionTheme;
import com.goodda.jejuday.mission.repository.MissionStepRepository;
import com.goodda.jejuday.mission.repository.MissionThemeRepository;
import com.goodda.jejuday.mission.repository.UserMissionCompletionRepository;
import com.goodda.jejuday.mission.repository.UserMissionStepRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MissionQueryService {

    private final MissionThemeRepository themeRepository;
    private final MissionStepRepository stepRepository;
    private final UserMissionStepRepository userStepRepository;
    private final UserMissionCompletionRepository completionRepository;
    private final SecurityUtil securityUtil;

    @Transactional(readOnly = true)
    public List<MissionThemeResponse> getMissionThemes() {
        Long userId = securityUtil.getAuthenticatedUser().getId();
        List<MissionTheme> themes = themeRepository.findAll();
        Set<Long> completedThemeIds = new HashSet<>(completionRepository.findCompletedThemeIds(userId));

        return themes.stream()
                .map(theme -> {
                    int completedSteps = (int) userStepRepository
                            .countByUser_IdAndMissionStep_MissionTheme_Id(userId, theme.getId());
                    boolean done = completedThemeIds.contains(theme.getId());
                    return MissionThemeResponse.of(theme, completedSteps, done);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MissionStepResponse> getMissionSteps(Long missionThemeId) {
        if (!themeRepository.existsById(missionThemeId)) {
            throw new EntityNotFoundException("Mission theme not found: " + missionThemeId);
        }
        Long userId = securityUtil.getAuthenticatedUser().getId();
        List<MissionStep> steps = stepRepository.findByMissionTheme_IdOrderByStepOrderAsc(missionThemeId);
        Set<Long> completedStepIds = new HashSet<>(userStepRepository.findCompletedStepIds(userId, missionThemeId));

        return steps.stream()
                .map(step -> MissionStepResponse.of(step, completedStepIds.contains(step.getId())))
                .toList();
    }
}
