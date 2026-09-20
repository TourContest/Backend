package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.entity.Like;
import com.goodda.jejuday.spot.entity.Spot;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    int countByTargetIdAndTargetType(Long targetId, Like.TargetType type);

    /**
     * 멱등 좋아요 INSERT. (user_id, target_type, target_id) 유니크 위반은 예외 대신 0행으로 흡수한다 —
     * 예외로 받으면 호출 트랜잭션이 rollback-only 가 되어 이후 로직(알림 등)까지 죽는다.
     *
     * @return 1 이면 새로 눌림, 0 이면 이미 눌려 있음(동시 요청 포함)
     */
    @Transactional
    @Modifying
    @Query(value = "INSERT IGNORE INTO spot_likes (user_id, spot_id, target_type, target_id, liked_at) "
            + "VALUES (:userId, :spotId, :targetType, :targetId, NOW())", nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("spotId") Long spotId,
            @Param("targetType") String targetType, @Param("targetId") Long targetId);

    /**
     * 좋아요 1건 원자적 삭제. 같은 유저의 동시 취소 중 하나만 1을 돌려받는다(나머지는 0) —
     * 엔티티 delete 는 이미 지워진 행을 또 지우려다 예외가 나고, 그 전에 조회한 값으로 like_count 를 이중 차감할 수 있다.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM Like l WHERE l.user.id = :userId AND l.targetType = :targetType AND l.targetId = :targetId")
    int deleteOne(@Param("userId") Long userId, @Param("targetType") Like.TargetType targetType, @Param("targetId") Long targetId);

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

    // 사용자가 좋아요한 스팟 조회 - 페이징 지원
    @Query("SELECT l.spot FROM Like l JOIN FETCH l.spot.user WHERE l.user.id = :userId AND l.targetType = :targetType AND (l.spot.isDeleted = false OR l.spot.isDeleted IS NULL) ORDER BY l.likedAt DESC")
    Page<Spot> findLikedSpotsByUserId(@Param("userId") Long userId, @Param("targetType") Like.TargetType targetType, Pageable pageable);
}