package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Like;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeRepository extends JpaRepository<Like, Long> {

    int countByTargetIdAndTargetType(Long targetId, Like.TargetType type);

    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, Like.TargetType type, Long targetId);

    void deleteByUserIdAndTargetTypeAndTargetId(Long userId, Like.TargetType type, Long targetId);
}