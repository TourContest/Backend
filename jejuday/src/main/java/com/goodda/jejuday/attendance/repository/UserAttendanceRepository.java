package com.goodda.jejuday.attendance.repository;

import com.goodda.jejuday.attendance.entity.UserAttendance;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAttendanceRepository extends JpaRepository<UserAttendance, Long> {
    Optional<UserAttendance> findByUserIdAndCheckDate(Long userId, LocalDate date);

    // 특정 날짜 이전에 출석 기록이 있는지 확인
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
            "FROM UserAttendance a WHERE a.user.id = :userId AND a.checkDate < :date")
    boolean existsByUserIdAndCheckDateBefore(@Param("userId") Long userId, @Param("date") LocalDate date);
}
