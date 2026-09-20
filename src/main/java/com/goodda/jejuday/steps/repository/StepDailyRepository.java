package com.goodda.jejuday.steps.repository;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.steps.entity.StepDaily;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StepDailyRepository extends JpaRepository<StepDaily, Long> {
    Optional<StepDaily> findByUserAndDate(User user, LocalDate date);

    /**
     * 오늘 첫 기록 시 (user_id, date) 행을 멱등하게 생성. 이미 있으면 조용히 0행 — 유니크 제약
     * 위반 예외로 받으면 같은 트랜잭션이 rollback-only로 마킹되므로 IGNORE로 흡수한다
     * (PointLedgerRepository.insertIgnore와 동일한 이유).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT IGNORE INTO step_daily "
            + "(user_id, date, total_steps, converted_points, level_reward_claimed, start_bonus_steps, start_bonus_applied, exchange_count) "
            + "VALUES (:userId, :date, 0, 0, false, 0, false, 0)", nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * 걸음수 포인트 전환의 한도(일일 캡)/횟수 검사를 DB에서 원자적으로 판정한다.
     * affected rows == 1일 때만 실제로 한도 내에서 전환이 이루어진 것이다 — 동시 요청으로 인해
     * 조회 시점과 UPDATE 시점 사이에 한도가 소진됐다면 0행이 반환되어 잘못된 초과 전환을 막는다.
     */
    /**
     * 걸음수 원자적 가산. 엔티티의 totalSteps += n 후 dirty-check UPDATE 는 read-modify-write 라서, 같은 유저의 동시 업로드가
     * 서로의 가산분을 덮어쓴다(lost update). flushAutomatically: 같은 트랜잭션에서 먼저 바뀐 엔티티 변경(시작 보너스 등)을
     * 이 UPDATE 이전에 반영해 순서를 보장한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE StepDaily d SET d.totalSteps = d.totalSteps + :steps WHERE d.id = :id")
    int addSteps(@Param("id") Long id, @Param("steps") long steps);

    /** addSteps 직후 같은 트랜잭션에서 읽으면 자신의 UPDATE 가 잡은 행 락 덕분에 정확히 "가산 직후 값"이다. */
    @Query("SELECT d.totalSteps FROM StepDaily d WHERE d.id = :id")
    Long findTotalStepsById(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE StepDaily d SET d.convertedPoints = d.convertedPoints + :pts, d.exchangeCount = d.exchangeCount + 1 "
            + "WHERE d.id = :id AND d.exchangeCount < :maxExchanges AND d.convertedPoints + :pts <= :dailyCap")
    int tryConsumeQuota(@Param("id") Long id, @Param("pts") int pts,
            @Param("maxExchanges") int maxExchanges, @Param("dailyCap") int dailyCap);

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
