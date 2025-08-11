package com.goodda.jejuday.auth.repository;

import com.goodda.jejuday.auth.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
