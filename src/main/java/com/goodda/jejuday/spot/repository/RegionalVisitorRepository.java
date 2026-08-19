package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.RegionalVisitor;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionalVisitorRepository extends JpaRepository<RegionalVisitor, Long> {
    Optional<RegionalVisitor> findByBaseDateAndRegionCodeAndRegionLevel(LocalDate date, String code, String level);
    List<RegionalVisitor> findTop100ByOrderByBaseDateDesc();
}
