package com.goodda.jejuday.auth.repository;

import com.goodda.jejuday.auth.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = "userThemes")
    Optional<User> findByEmail(String email);

    Optional<User> findById(Long id);

    @Query("select distinct u from User u left join fetch u.userThemes")
    List<User> findAllWithThemes();

    @Query("select u from User u left join fetch u.userThemes where u.id = :id")
    Optional<User> findByIdWithThemes(@Param("id") Long id);

    Optional<User> findByNickname(String nickname);

    boolean existsByNickname(String nickname);

    boolean existsByEmail(String email);

    void deleteByEmail(String email);

    /**
     * 알림이 활성화된 사용자 수 조회
     */
    long countByIsNotificationEnabledTrue();

    /**
     * FCM 토큰이 있는 사용자 수 조회
     */
    long countByFcmTokenIsNotNull();

    /**
     * 알림 활성화 + FCM 토큰이 있는 사용자 목록 조회 (브로드캐스트용)
     */
    List<User> findByIsNotificationEnabledTrueAndFcmTokenIsNotNull();

    /**
     * hallabong 원자적 조건부 차감.
     * hallabong >= cost 조건을 만족할 때만 차감하며, 갱신된 행 수를 반환한다.
     * 반환값이 0이면 잔액 부족 또는 유저 미존재.
     *
     * <p>flushAutomatically = true: 같은 트랜잭션에서 이 User(또는 다른 엔티티)에 대한 미반영
     * 변경이 있다면 이 벌크 UPDATE 실행 전에 먼저 flush해 순서를 보장한다. clearAutomatically는
     * 쓰지 않는다 — 영속성 컨텍스트 전체를 비우면 같은 트랜잭션에서 이미 로드된 다른 엔티티
     * (Product, ChallengeParticipation 등)까지 detach되어 지연 로딩/dirty-check가 깨질 수 있다.
     * User.hallabong은 @DynamicUpdate로 보호되므로(직접 setHallabong 없이는 UPDATE 컬럼에
     * 포함되지 않음) 이후 같은 트랜잭션에서 User를 save()해도 이 값을 덮어쓰지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE User u SET u.hallabong = u.hallabong - :cost WHERE u.id = :id AND u.hallabong >= :cost")
    int decrementHallabong(@Param("id") Long id, @Param("cost") int cost);

    /** hallabong 원자적 가산. PointLedgerService.record 전용 — 직접 호출하지 말 것. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE User u SET u.hallabong = u.hallabong + :amount WHERE u.id = :id")
    int incrementHallabong(@Param("id") Long id, @Param("amount") int amount);

    /**
     * 같은 트랜잭션 내에서 잔액 벌크 UPDATE 직후 최신값을 읽을 때 사용.
     * findById()는 1차 캐시에 이미 로드된 User가 있으면 벌크 UPDATE 결과를 무시하고
     * 그 stale 인스턴스를 반환하지만, 스칼라 프로젝션 쿼리는 항상 DB를 직접 조회한다.
     */
    @Query("SELECT u.hallabong FROM User u WHERE u.id = :id")
    Integer findHallabongById(@Param("id") Long id);

    /** totalSteps 원자적 가산 — user.setTotalSteps(+delta) 형태의 read-modify-write 레이스 제거 */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE User u SET u.totalSteps = u.totalSteps + :delta WHERE u.id = :id")
    int incrementTotalSteps(@Param("id") Long id, @Param("delta") long delta);

    /** PointLedgerReconciliationJob 전용 — 전체 사용자의 (id, hallabong) 스칼라 프로젝션 1방 조회 */
    @Query("SELECT u.id, u.hallabong FROM User u")
    List<Object[]> findAllIdAndHallabong();

    /** PointLedgerReconciliationJob 전용 — 드리프트 교정 시 원장 합계로 잔액을 직접 덮어쓴다 */
    @Modifying
    @Query("UPDATE User u SET u.hallabong = :balance WHERE u.id = :id")
    int setHallabongExact(@Param("id") Long id, @Param("balance") int balance);
}
