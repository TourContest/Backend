package com.goodda.jejuday.spot.repository;

import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.entity.Spot.SpotType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    // 1) 최신순 (커뮤니티 피드 - 관광공사 데이터 제외, 유저 작성 글만 / 차단한 유저의 글은 제외)
    @EntityGraph(attributePaths = {"user"})
    @Query("""
        SELECT s FROM Spot s
        WHERE s.type IN :types AND s.isDeleted = false AND s.userCreated = true
          AND (s.user IS NULL OR s.user.id NOT IN :blockedUserIds)
        ORDER BY s.createdAt DESC
        """)
    Page<Spot> findByTypeInOrderByCreatedAtDesc(
            @Param("types") Iterable<Spot.SpotType> types,
            @Param("blockedUserIds") List<Long> blockedUserIds,
            Pageable pageable);

    // 2) 조회수순 (커뮤니티 피드 - 관광공사 데이터 제외, 유저 작성 글만 / 차단한 유저의 글은 제외)
    @EntityGraph(attributePaths = {"user"})
    @Query("""
        SELECT s FROM Spot s
        WHERE s.type IN :types AND s.isDeleted = false AND s.userCreated = true
          AND (s.user IS NULL OR s.user.id NOT IN :blockedUserIds)
        ORDER BY s.viewCount DESC
        """)
    Page<Spot> findByTypeInOrderByViewCountDesc(
            @Param("types") Iterable<Spot.SpotType> types,
            @Param("blockedUserIds") List<Long> blockedUserIds,
            Pageable pageable);

    // 3) 좋아요순 (커뮤니티 피드 - 관광공사 데이터 제외, 유저 작성 글만 / 차단한 유저의 글은 제외)
    @EntityGraph(attributePaths = {"user"})
    @Query("""
        SELECT s FROM Spot s
        WHERE s.type IN :types AND s.isDeleted = false AND s.userCreated = true
          AND (s.user IS NULL OR s.user.id NOT IN :blockedUserIds)
        ORDER BY s.likeCount DESC
        """)
    Page<Spot> findByTypeInOrderByLikeCountDesc(
            @Param("types") Iterable<Spot.SpotType> types,
            @Param("blockedUserIds") List<Long> blockedUserIds,
            Pageable pageable);

    // 트라이 초기화용: SPOT + CHALLENGE
    List<Spot> findAllByTypeIn(List<SpotType> types);

    boolean existsByExternalPlaceId(String externalPlaceId);

    Optional<Spot> findFirstByNameIgnoreCaseAndUserCreatedFalse(String name);

    // 커뮤니티 검색: 이름/제목/태그 중 하나라도 포함 + 타입 필터링 + 삭제되지 않은 글만
    // + 관광공사 데이터 제외(유저 작성 글만) + 차단한 유저의 글 제외
    // user를 함께 fetch하지 않으면, 컨트롤러에서 s.getUser()를 호출할 때(트랜잭션/세션이 이미 끝난 뒤)
    // LazyInitializationException이 터져 500이 난다.
    @EntityGraph(attributePaths = {"user"})
    @Query("""
        SELECT s FROM Spot s
        WHERE s.type IN :types
          AND (s.isDeleted = false OR s.isDeleted IS NULL)
          AND s.userCreated = true
          AND (s.user IS NULL OR s.user.id NOT IN :blockedUserIds)
          AND (
            LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.title) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.tag1) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.tag2) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(s.tag3) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<Spot> searchByNameOrTitleOrTagAndTypeIn(@Param("query") String query,
                                                  @Param("types") List<SpotType> types,
                                                  @Param("blockedUserIds") List<Long> blockedUserIds,
                                                  Pageable pageable);

    // 사용자 정보를 함께 페치하여 N+1 문제 해결
    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.type IN :types ORDER BY s.createdAt DESC")
    Page<Spot> findByTypeInOrderByCreatedAtDescWithUser(@Param("types") Iterable<Spot.SpotType> types, Pageable pageable);

    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.type IN :types ORDER BY s.viewCount DESC")
    Page<Spot> findByTypeInOrderByViewCountDescWithUser(@Param("types") Iterable<Spot.SpotType> types, Pageable pageable);

    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.type IN :types ORDER BY s.likeCount DESC")
    Page<Spot> findByTypeInOrderByLikeCountDescWithUser(@Param("types") Iterable<Spot.SpotType> types, Pageable pageable);

    // 승격 프로세스용 - 삭제되지 않은 모든 스팟을 사용자 정보와 함께 조회
    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.isDeleted = false OR s.isDeleted IS NULL")
    List<Spot> findAllActiveSpotsWithUser();

    // 특정 타입의 스팟들을 사용자 정보와 함께 조회
    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.type = :type AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    List<Spot> findByTypeWithUser(@Param("type") Spot.SpotType type);

    // 특정 기간 이후 생성된 스팟들 조회
    @Query("SELECT s FROM Spot s JOIN FETCH s.user WHERE s.createdAt >= :since AND (s.isDeleted = false OR s.isDeleted IS NULL)")
    List<Spot> findActiveSpotsCreatedAfterWithUser(@Param("since") LocalDateTime since);

    // 특정 기간 이전 생성된 스팟들 조회 (정리용)
    @Query("SELECT s.id FROM Spot s WHERE s.createdAt < :before")
    List<Long> findSpotIdsCreatedBefore(@Param("before") LocalDateTime before);

    // 상태 전이 원자화 — affected rows == 1일 때만 알림 발송 (중복 실행에도 전이/알림 1회 보장)
    @Modifying
    @Query("UPDATE Spot s SET s.type = :to WHERE s.id = :id AND s.type = :from")
    int transition(@Param("id") Long id, @Param("from") SpotType from, @Param("to") SpotType to);

    // 승격 대상 POST 타입 스팟들 (점수 계산 최적화를 위해 필요한 정보만 조회)
    @Query("""
        SELECT s FROM Spot s 
        JOIN FETCH s.user 
        WHERE s.type = 'POST' 
        AND (s.isDeleted = false OR s.isDeleted IS NULL)
        AND s.createdAt >= :cutoffDate
    """)
    List<Spot> findPromotionCandidatePosts(@Param("cutoffDate") LocalDateTime cutoffDate);

    // 승격 대상 SPOT 타입 스팟들 (UGC 출신만 - TourAPI로 동기화된 공공데이터 스팟은 externalPlaceId가 있어 제외됨)
    @Query("""
        SELECT s FROM Spot s
        JOIN FETCH s.user
        WHERE s.type = 'SPOT'
        AND s.externalPlaceId IS NULL
        AND (s.isDeleted = false OR s.isDeleted IS NULL)
        AND s.createdAt >= :cutoffDate
    """)
    List<Spot> findPromotionCandidateSpots(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("""
        SELECT s FROM Spot s
        LEFT JOIN FETCH s.user
        LEFT JOIN FETCH s.theme
        WHERE s.id = :id
    """)
    Optional<Spot> findDetailWithUserAndTagsById(@Param("id") Long id);

    // 최근 N개 id만 가볍게 뽑기(테마 선택 가능) → 메모리에서 랜덤
    @Query("""
   select s.id
   from Spot s
   where s.type = 'CHALLENGE'
     and (s.isDeleted = false or s.isDeleted is null)
     and (:themeId is null or s.theme.id = :themeId)
   order by s.id desc
""")
    List<Long> findRecentChallengeIds(@Param("themeId") Long themeId,
                                      org.springframework.data.domain.Pageable pageable);

    // 챌린지 추천 풀 - 관광데이터(TourAPI 동기화, 공식) 스팟만. userCreated=false가 곧 TourAPI 출처라는 뜻
    // (SpotTourSyncService가 동기화 시 항상 이 값을 false로 저장함).
    @Query("""
   select s.id
   from Spot s
   where s.type = 'SPOT'
     and s.userCreated = false
     and (s.isDeleted = false or s.isDeleted is null)
     and (:themeId is null or s.theme.id = :themeId)
   order by s.id desc
""")
    List<Long> findRecentOfficialSpotIds(@Param("themeId") Long themeId,
                                         org.springframework.data.domain.Pageable pageable);


    // 아무거나 1개 (중복 허용 백업용)
    @Query("""
   select s
   from Spot s
   where s.type = 'CHALLENGE'
     and (s.isDeleted = false or s.isDeleted is null)
   order by s.id desc
""")
    List<Spot> findAnyChallengeSpot(org.springframework.data.domain.Pageable pageable);

    // 사용자가 작성한 게시글 조회 (삭제되지 않은 것만) - 페이징 지원
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT s FROM Spot s WHERE s.user.id = :userId AND (s.isDeleted = false OR s.isDeleted IS NULL) ORDER BY s.createdAt DESC")
    Page<Spot> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
    
    // 사용자가 작성한 게시글 조회 - 조회수순
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT s FROM Spot s WHERE s.user.id = :userId AND (s.isDeleted = false OR s.isDeleted IS NULL) ORDER BY s.viewCount DESC")
    Page<Spot> findByUserIdOrderByViewCountDesc(@Param("userId") Long userId, Pageable pageable);
    
    // 사용자가 작성한 게시글 조회 - 댓글 많은 순 (댓글 수는 서비스 레이어에서 계산)
    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT s FROM Spot s WHERE s.user.id = :userId AND (s.isDeleted = false OR s.isDeleted IS NULL) ORDER BY s.createdAt DESC")
    Page<Spot> findByUserIdOrderByCreatedAtDescForCommentSort(@Param("userId") Long userId, Pageable pageable);

    // TourAPI 상세정보(SpotDetail)가 아직 없는 SPOT 타입 스팟 (배치 보강 대상)
    @Query("""
        SELECT s FROM Spot s
        WHERE s.type = 'SPOT'
          AND s.externalPlaceId IS NOT NULL
          AND (s.isDeleted = false OR s.isDeleted IS NULL)
          AND NOT EXISTS (SELECT 1 FROM SpotDetail d WHERE d.spotId = s.id)
        ORDER BY s.id ASC
    """)
    List<Spot> findMissingDetailSpots(Pageable pageable);

    // 추천 후보 대상(SPOT/CHALLENGE) 중 임베딩이 아직 없는 스팟 (배치 보강 대상)
    @Query("""
        SELECT s FROM Spot s
        WHERE s.type IN ('SPOT', 'CHALLENGE')
          AND (s.isDeleted = false OR s.isDeleted IS NULL)
          AND NOT EXISTS (SELECT 1 FROM SpotEmbedding se WHERE se.spotId = s.id)
        ORDER BY s.id ASC
    """)
    List<Spot> findMissingEmbeddingSpots(Pageable pageable);

}
