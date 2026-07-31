package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.SpotEmbedding;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpotEmbeddingRepository extends JpaRepository<SpotEmbedding, Long> {
    Optional<SpotEmbedding> findBySpotId(Long spotId);
    List<SpotEmbedding> findBySpotIdIn(Collection<Long> spotIds);
}
