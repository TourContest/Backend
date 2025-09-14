package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Spot;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like, Long> {

    int countByTargetIdAndTargetType(Long targetId, Like.TargetType type);

    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, Like.TargetType type, Long targetId);

    void deleteByUserIdAndTargetTypeAndTargetId(Long userId, Like.TargetType type, Long targetId);

    long countBySpotId(Long spotId);

    boolean existsByUserIdAndSpotId(Long userId, Long spotId);

    /**
     * 해당 유저가 해당 스팟에 좋아요를 눌렀는지 확인
     */
    boolean existsByUserAndSpot(User user, Spot spot);

    /**
     * 해당 유저가 해당 스팟에 눌렀던 좋아요 엔티티를 조회
     */
    Optional<Like> findByUserAndSpot(User user, Spot spot);

    /**
     * 스팟 전체 좋아요 개수 조회
     */
    long countBySpot(Spot spot);

    boolean existsByUser_IdAndTargetIdAndTargetType(Long userId, Long targetId, Like.TargetType targetType);
    Optional<Like> findByUser_IdAndTargetIdAndTargetType(Long userId, Long targetId, Like.TargetType targetType);

    // 배치 조회를 위한 메서드들 (N+1 문제 해결)
    @Query("""
        SELECT l.targetId, COUNT(l) 
        FROM Like l 
        WHERE l.targetId IN :targetIds AND l.targetType = :targetType 
        GROUP BY l.targetId
    """)
    List<Object[]> countByTargetIdsAndTargetType(@Param("targetIds") List<Long> targetIds,
                                                 @Param("targetType") Like.TargetType targetType);

    // 스팟 ID 목록에 대한 좋아요 수를 Map으로 반환하는 기본 메서드
    default Map<Long, Long> getLikeCountsForSpots(List<Long> spotIds) {
        if (spotIds == null || spotIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> results = countByTargetIdsAndTargetType(spotIds, Like.TargetType.SPOT);
        return results.stream()
                .collect(java.util.stream.Collectors.toMap(
                        result -> (Long) result[0],
                        result -> (Long) result[1]
                ));
    }

    // 특정 사용자가 여러 스팟에 좋아요를 눌렀는지 배치 확인 (올바른 필드명 사용)
    @Query("""
        SELECT l.targetId 
        FROM Like l 
        WHERE l.user.id = :userId 
        AND l.targetId IN :targetIds 
        AND l.targetType = :targetType
    """)
    List<Long> findLikedTargetIds(@Param("userId") Long userId,
                                  @Param("targetIds") List<Long> targetIds,
                                  @Param("targetType") Like.TargetType targetType);

    // 스팟별 좋아요 수 조회 (캐싱용) - 더 안전한 버전
    @Query("""
        SELECT l.spot.id, COUNT(l) 
        FROM Like l 
        WHERE l.spot.id IN :spotIds
        GROUP BY l.spot.id
    """)
    List<Object[]> getLikeCountMapForSpotsDirect(@Param("spotIds") List<Long> spotIds);
}