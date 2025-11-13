package com.goodda.jejuday.spot.tourapi;

import com.goodda.jejuday.spot.entity.Spot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpotTourRepository extends JpaRepository<Spot, Long> {

    @Query("select s from Spot s where s.externalPlaceId = :extId")
    Optional<Spot> findByExternalPlaceId(@Param("extId") String extId);
}
