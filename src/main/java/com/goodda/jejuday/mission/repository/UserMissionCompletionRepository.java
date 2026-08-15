package com.goodda.jejuday.mission.repository;

import com.goodda.jejuday.mission.entity.UserMissionCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserMissionCompletionRepository extends JpaRepository<UserMissionCompletion, Long> {

    boolean existsByUser_IdAndMissionTheme_Id(Long userId, Long missionThemeId);

    @Query("SELECT c.missionTheme.id FROM UserMissionCompletion c WHERE c.user.id = :userId")
    List<Long> findCompletedThemeIds(@Param("userId") Long userId);
}
