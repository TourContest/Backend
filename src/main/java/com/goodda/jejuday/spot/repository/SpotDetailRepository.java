package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.SpotDetail;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotDetailRepository extends JpaRepository<SpotDetail, Long> {
    Optional<SpotDetail> findBySpotId(Long spotId);
    List<SpotDetail> findBySpotIdIn(Collection<Long> spotIds);
}
