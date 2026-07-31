package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.SpotCongestion;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotCongestionRepository extends JpaRepository<SpotCongestion, Long> {
    Optional<SpotCongestion> findBySpotIdAndCongestionDate(Long spotId, LocalDate congestionDate);
    List<SpotCongestion> findBySpotIdInAndCongestionDate(Collection<Long> spotIds, LocalDate congestionDate);
}
