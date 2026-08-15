package com.goodda.jejuday.mission.repository;

import com.goodda.jejuday.mission.entity.MissionStep;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionStepRepository extends JpaRepository<MissionStep, Long> {

    @EntityGraph(attributePaths = {"spot"})
    List<MissionStep> findByMissionTheme_IdOrderByStepOrderAsc(Long missionThemeId);

    // 챌린지 완료 트리거: 방금 완료한 spotId가 어떤 미션 스텝에 속해있는지 조회
    List<MissionStep> findBySpot_Id(Long spotId);
}
