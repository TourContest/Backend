package com.goodda.jejuday.attendance.repository;

import com.goodda.jejuday.attendance.entity.UserBonusLog;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBonusLogRepository extends JpaRepository<UserBonusLog, Long> {
    boolean existsByUserIdAndBonusTypeAndGivenDate(Long userId, String bonusType, LocalDate date);
}
