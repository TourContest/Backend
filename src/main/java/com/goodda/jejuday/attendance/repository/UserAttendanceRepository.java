package com.goodda.jejuday.attendance.repository;

import com.goodda.jejuday.attendance.entity.UserAttendance;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAttendanceRepository extends JpaRepository<UserAttendance, Long> {

    Optional<UserAttendance> findByUserIdAndCheckDate(Long userId, LocalDate date);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
            "FROM UserAttendance a WHERE a.user.id = :userId AND a.checkDate < :date")
    boolean existsByUserIdAndCheckDateBefore(@Param("userId") Long userId, @Param("date") LocalDate date);

    interface ReminderTarget {
        Long getId();
        String getFcmToken();
    }

    @Query("""
            SELECT u.id AS id, u.fcmToken AS fcmToken
            FROM User u
            WHERE u.isNotificationEnabled = true
              AND u.fcmToken IS NOT NULL
              AND u.fcmToken <> ''
            """)
    List<ReminderTarget> findAttendanceReminderTargets();
}
