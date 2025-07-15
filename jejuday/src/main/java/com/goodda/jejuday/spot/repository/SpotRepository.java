package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SpotRepository extends JpaRepository<Spot, Long> {
    @Query("SELECT s FROM Spot s WHERE s.isDeleted = false AND FUNCTION('distance', s.latitude, s.longitude, :lat, :lng) <= :radius")
    List<Spot> findWithinRadius(@Param("lat") BigDecimal lat, @Param("lng") BigDecimal lng, @Param("radius") int radius);
}