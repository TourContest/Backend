package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.SpotRelation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotRelationRepository extends JpaRepository<SpotRelation, Long> {
    List<SpotRelation> findTop20BySourceSpotIdOrderByRelationRankAsc(Long sourceSpotId);
    Optional<SpotRelation> findBySourceSpotIdAndTargetSpotIdAndRelationType(Long sourceSpotId, Long targetSpotId, String type);
    List<SpotRelation> findBySourceSpotIdIn(Collection<Long> sourceSpotIds);
}
