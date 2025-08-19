package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.ChallengeParticipation;
import com.goodda.jejuday.spot.entity.ChallengeParticipation.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChallengeParticipationRepository
        extends JpaRepository<ChallengeParticipation, Long> {

    boolean existsByChallenge_IdAndUser_Id(Long challengeId, Long userId);

    Optional<ChallengeParticipation> findByChallenge_IdAndUser_Id(Long challengeId, Long userId);

    long countByChallenge_Id(Long challengeId);

    Page<ChallengeParticipation> findByUser_IdAndStatus(Long userId, Status status, Pageable pageable);

    Page<ChallengeParticipation> findByUser_Id(Long userId, Pageable pageable);
}