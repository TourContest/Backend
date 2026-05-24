package com.goodda.jejuday.notification.repository;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.entity.NotificationEntity;
import com.goodda.jejuday.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    /**
     * 사용자의 모든 알림을 최신순으로 조회
     */
    List<NotificationEntity> findAllByUserOrderByCreatedAtDesc(User user);

    /**
     * 사용자의 알림을 페이징으로 조회 (성능 개선)
     */
    Page<NotificationEntity> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    /**
     * 사용자의 읽지 않은 알림 개수 조회
     */
    long countByUserAndIsRead(User user, boolean isRead);

    /**
     * 사용자의 읽지 않은 알림 목록 조회
     */
    List<NotificationEntity> findByUserAndIsRead(User user, boolean isRead);

    /**
     * 특정 사용자의 특정 알림 삭제
     */
    void deleteByIdAndUser(Long id, User user);

    /**
     * 사용자의 모든 알림 삭제
     */
    void deleteAllByUser(User user);

    /**
     * 특정 타입의 알림들을 조회
     */
    List<NotificationEntity> findByUserAndType(User user, NotificationType type);

    /**
     * 특정 기간 이후의 알림들을 조회
     */
    List<NotificationEntity> findByUserAndCreatedAtAfter(User user, LocalDateTime after);

    /**
     * 오래된 알림들을 배치로 삭제 (성능 개선)
     */
    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.createdAt < :cutoffDate")
    int deleteOldNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * 사용자별 오래된 알림들을 배치로 삭제
     */
    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.user = :user AND n.createdAt < :cutoffDate")
    int deleteOldNotificationsByUser(@Param("user") User user, @Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * 특정 사용자의 읽은 알림들을 배치로 삭제
     */
    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.user = :user AND n.isRead = true")
    int deleteReadNotificationsByUser(@Param("user") User user);

    /**
     * 사용자의 모든 알림을 읽음 처리 (배치 업데이트)
     */
    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true WHERE n.user = :user AND n.isRead = false")
    int markAllAsReadByUser(@Param("user") User user);

    /**
     * 특정 타입의 알림 개수 조회
     */
    long countByUserAndType(User user, NotificationType type);

    /**
     * 최근 N일간의 알림 개수 조회
     */
    @Query("SELECT COUNT(n) FROM NotificationEntity n WHERE n.user = :user AND n.createdAt >= :since")
    long countRecentNotifications(@Param("user") User user, @Param("since") LocalDateTime since);

    /**
     * 사용자의 최근 알림들을 제한된 개수로 조회 (대시보드용)
     */
    @Query("SELECT n FROM NotificationEntity n WHERE n.user = :user ORDER BY n.createdAt DESC")
    List<NotificationEntity> findRecentNotifications(@Param("user") User user, Pageable pageable);

    /**
     * 읽지 않은 알림들을 타입별로 그룹화하여 개수 조회
     */
    @Query("""
        SELECT n.type, COUNT(n) 
        FROM NotificationEntity n 
        WHERE n.user = :user AND n.isRead = false 
        GROUP BY n.type
    """)
    List<Object[]> countUnreadNotificationsByType(@Param("user") User user);

    /**
     * 특정 기간 동안의 알림 통계 조회
     */
    @Query("""
        SELECT DATE(n.createdAt), n.type, COUNT(n) 
        FROM NotificationEntity n 
        WHERE n.user = :user 
        AND n.createdAt BETWEEN :startDate AND :endDate 
        GROUP BY DATE(n.createdAt), n.type 
        ORDER BY DATE(n.createdAt) DESC
    """)
    List<Object[]> getNotificationStatistics(@Param("user") User user,
                                             @Param("startDate") LocalDateTime startDate,
                                             @Param("endDate") LocalDateTime endDate);

    // 관리자 서비스용 추가 메서드들

    /**
     * 전체 사용자 수 조회
     */
    @Query("SELECT COUNT(DISTINCT n.user) FROM NotificationEntity n")
    long countDistinctUsers();

    /**
     * 특정 타입의 알림 개수 조회
     */
    long countByType(NotificationType type);

    /**
     * 특정 타입과 기간으로 알림 개수 조회
     */
    long countByTypeAndCreatedAtBetween(NotificationType type, LocalDateTime start, LocalDateTime end);

    /**
     * 특정 타입과 읽음 상태로 알림 개수 조회
     */
    long countByTypeAndIsRead(NotificationType type, boolean isRead);

    /**
     * 특정 시간 이후 생성된 알림 개수 조회
     */
    long countByCreatedAtAfter(LocalDateTime after);

    /**
     * 특정 사용자의 총 알림 개수 조회
     */
    long countByUser(User user);
}