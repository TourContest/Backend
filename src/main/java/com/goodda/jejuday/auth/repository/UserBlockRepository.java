package com.goodda.jejuday.auth.repository;

import com.goodda.jejuday.auth.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    Optional<UserBlock> findByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    void deleteByBlocker_IdAndBlocked_Id(Long blockerId, Long blockedId);

    @Query("SELECT b.blocked.id FROM UserBlock b WHERE b.blocker.id = :blockerId")
    List<Long> findBlockedUserIds(@Param("blockerId") Long blockerId);

    @Query("SELECT b FROM UserBlock b JOIN FETCH b.blocked WHERE b.blocker.id = :blockerId ORDER BY b.createdAt DESC")
    List<UserBlock> findAllByBlockerIdWithBlockedUser(@Param("blockerId") Long blockerId);
}
