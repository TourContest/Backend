package com.goodda.jejuday.mission.repository;

import com.goodda.jejuday.mission.entity.MissionTheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MissionThemeRepository extends JpaRepository<MissionTheme, Long> {
    Optional<MissionTheme> findByTitle(String title);
}
