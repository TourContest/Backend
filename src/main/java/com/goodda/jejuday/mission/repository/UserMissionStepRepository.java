package com.goodda.jejuday.mission.repository;

import com.goodda.jejuday.mission.entity.UserMissionStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserMissionStepRepository extends JpaRepository<UserMissionStep, Long> {

    boolean existsByUser_IdAndMissionStep_Id(Long userId, Long missionStepId);

    long countByUser_IdAndMissionStep_MissionTheme_Id(Long userId, Long missionThemeId);

    @Query("SELECT s.missionStep.id FROM UserMissionStep s WHERE s.user.id = :userId AND s.missionStep.missionTheme.id = :missionThemeId")
    List<Long> findCompletedStepIds(@Param("userId") Long userId, @Param("missionThemeId") Long missionThemeId);
}
