package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.ChallengeRecoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChallengeRecoItemRepository extends JpaRepository<ChallengeRecoItem, Long> {

    @Query("""
    select r
    from ChallengeRecoItem r
    where r.userId = :userId
      and r.expiresAt >= :now
    order by r.slotIndex asc
    """)
    List<ChallengeRecoItem> findActiveByUser(@Param("userId") Long userId,
                                             @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from ChallengeRecoItem r where r.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from ChallengeRecoItem r where r.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}