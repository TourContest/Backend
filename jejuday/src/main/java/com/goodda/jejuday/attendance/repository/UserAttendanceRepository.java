package com.goodda.jejuday.attendance.repository;

import com.goodda.jejuday.attendance.entity.UserAttendance;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAttendanceRepository extends JpaRepository<UserAttendance, Long> {
    Optional<UserAttendance> findByUserIdAndCheckDate(Long userId, LocalDate date);
}
