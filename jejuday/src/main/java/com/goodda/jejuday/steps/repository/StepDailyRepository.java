package com.goodda.jejuday.steps.repository;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.steps.entity.StepDaily;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StepDailyRepository extends JpaRepository<StepDaily, Long> {
    Optional<StepDaily> findByUserAndDate(User user, LocalDate date);

    // DailyResetScheduler에서 사용하는 메서드 추가
    List<StepDaily> findAllByDate(LocalDate date);

    // 사용자의 전체 걸음수 합계 조회 (옵션)
    @Query("SELECT SUM(s.totalSteps) FROM StepDaily s WHERE s.user = :user")
    Long getTotalStepsByUser(@Param("user") User user);

    // 전날 걸음수 조회
    @Query("SELECT s FROM StepDaily s WHERE s.user = :user AND s.date = :date")
    Optional<StepDaily> findPreviousDaySteps(@Param("user") User user, @Param("date") LocalDate date);

    // 사용자의 최근 N일 데이터 조회
    @Query("SELECT s FROM StepDaily s WHERE s.user = :user AND s.date >= :startDate ORDER BY s.date DESC")
    List<StepDaily> findRecentDays(@Param("user") User user, @Param("startDate") LocalDate startDate);
}
