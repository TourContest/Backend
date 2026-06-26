package com.goodda.jejuday.attendance.repository;

import com.goodda.jejuday.attendance.entity.UserAttendance;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAttendanceRepository extends JpaRepository<UserAttendance, Long> {
    Optional<UserAttendance> findByUserIdAndCheckDate(Long userId, LocalDate date);

    // 특정 날짜 이전에 출석 기록이 있는지 확인
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END " +
            "FROM UserAttendance a WHERE a.user.id = :userId AND a.checkDate < :date")
    boolean existsByUserIdAndCheckDateBefore(@Param("userId") Long userId, @Param("date") LocalDate date);

    // LENGTH 조건은 NotificationService.FCM_TOKEN_MIN_LENGTH(20)과 동일해야 한다
    @Query("""
    select u.id as id, u.fcmToken as fcmToken
    from User u
    where u.isNotificationEnabled = true
      and u.fcmToken is not null
      and length(u.fcmToken) > 20
""")
    List<ReminderTarget> findAttendanceReminderTargets();

    /**
     * 특정 날짜에 출석한 유저 ID Set — Redis cold cache 폴백용.
     */
    @Query("SELECT a.user.id FROM UserAttendance a WHERE a.checkDate = :date")
    Set<Long> findAttendedUserIdsByDate(@Param("date") LocalDate date);
}
