package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterAndTargetTypeAndTargetId(User reporter, Report.TargetType targetType, Long targetId);
}
