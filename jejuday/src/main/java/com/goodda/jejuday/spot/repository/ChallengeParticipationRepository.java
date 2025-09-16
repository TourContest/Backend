package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.ChallengeParticipation;
import com.goodda.jejuday.spot.entity.ChallengeParticipation.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChallengeParticipationRepository extends JpaRepository<ChallengeParticipation, Long> {

    @Query("""
       select cp from ChallengeParticipation cp
       join fetch cp.challenge s
       left join fetch s.theme
       where cp.user.id = :userId and cp.status in :statuses
       order by cp.joinedAt desc
    """)
    List<ChallengeParticipation> findOngoing(@Param("userId") Long userId,
                                             @Param("statuses") List<Status> statuses,
                                             Pageable pageable);

    // 전체 진행중인 챌린지 조회 (페이징 없음) - 추천 생성용
    @Query("""
       select cp from ChallengeParticipation cp
       join fetch cp.challenge s
       where cp.user.id = :userId and cp.status in :statuses
    """)
    List<ChallengeParticipation> findOngoingAll(@Param("userId") Long userId,
                                                @Param("statuses") List<Status> statuses);

    @Query("""
       select distinct s.theme.id from ChallengeParticipation cp
       join cp.challenge s
       where cp.user.id = :userId and cp.status in :statuses and s.theme is not null
    """)
    List<Long> findOngoingThemeIds(@Param("userId") Long userId,
                                   @Param("statuses") List<Status> statuses);

    @Query("""
   select cp from ChallengeParticipation cp
   join fetch cp.challenge s
   left join fetch s.theme
   where cp.user.id = :userId
     and cp.status = com.goodda.jejuday.spot.entity.ChallengeParticipation.Status.COMPLETED
     and (:lastId is null or cp.id < :lastId)
   order by cp.completedAt desc, cp.id desc
""")
    List<ChallengeParticipation> findCompletedLatest(@Param("userId") Long userId,
                                                     @Param("lastId") Long lastId,
                                                     Pageable pageable);

    @Query("""
   select cp from ChallengeParticipation cp
   join fetch cp.challenge s
   left join fetch s.theme
   where cp.user.id = :userId
     and cp.status = com.goodda.jejuday.spot.entity.ChallengeParticipation.Status.COMPLETED
     and (:lastId is null or cp.id < :lastId)
   order by s.viewCount desc, cp.id desc
""")
    List<ChallengeParticipation> findCompletedByViews(@Param("userId") Long userId,
                                                      @Param("lastId") Long lastId,
                                                      Pageable pageable);

    @Query("""
   select cp from ChallengeParticipation cp
   join fetch cp.challenge s
   left join fetch s.theme
   where cp.user.id = :userId
     and cp.status = com.goodda.jejuday.spot.entity.ChallengeParticipation.Status.COMPLETED
     and (:lastId is null or cp.id < :lastId)
   order by s.likeCount desc, cp.id desc
""")
    List<ChallengeParticipation> findCompletedByLikes(@Param("userId") Long userId,
                                                      @Param("lastId") Long lastId,
                                                      Pageable pageable);

    Optional<ChallengeParticipation> findByChallenge_IdAndUser_Id(Long challengeId, Long userId);

    boolean existsByChallenge_IdAndUser_Id(Long challengeId, Long userId);

    // 현재 로그인된 유저가 특정 챌린지(Spot)에 JOINED 상태로 참여 중이며, 해당 참여 기간이 오늘 기준 진행 중인지 확인
    boolean existsByUser_IdAndChallenge_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long userId, Long challengeId, ChallengeParticipation.Status status,
            LocalDate currentDate1, LocalDate currentDate2
    );


    @Query("""
        select cp.challenge.id
          from ChallengeParticipation cp
         where cp.user.id = :userId
           and cp.challenge.id in :challengeIds
           and cp.status = com.goodda.jejuday.spot.entity.ChallengeParticipation.Status.JOINED
           and (cp.startDate is null or cp.startDate <= :today)
           and (cp.endDate   is null or cp.endDate   >= :today)
    """)
    List<Long> findActiveChallengeIdsForUserOnDate(@Param("userId") Long userId,
                                                   @Param("challengeIds") Collection<Long> challengeIds,
                                                   @Param("today") LocalDate today);
}