package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SpotRepository extends JpaRepository<Spot, Long> {
    @Query(value = """
    SELECT * FROM spot s
    WHERE s.is_deleted = false AND (
        6371 * acos(
            cos(radians(:lat)) *
            cos(radians(s.latitude)) *
            cos(radians(s.longitude) - radians(:lng)) +
            sin(radians(:lat)) *
            sin(radians(s.latitude))
        )
    ) <= :radius
""", nativeQuery = true)
    List<Spot> findWithinRadius(@Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng, @Param("radius") int radius);

}