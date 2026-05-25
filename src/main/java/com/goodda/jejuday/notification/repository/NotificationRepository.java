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

    Page<NotificationEntity> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    List<NotificationEntity> findAllByUserOrderByCreatedAtDesc(User user);

    long countByUserAndIsRead(User user, boolean isRead);

    List<NotificationEntity> findByUserAndIsRead(User user, boolean isRead);

    void deleteByIdAndUser(Long id, User user);

    void deleteAllByUser(User user);

    List<NotificationEntity> findByUserAndType(User user, NotificationType type);

    List<NotificationEntity> findByUserAndCreatedAtAfter(User user, LocalDateTime after);

    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.createdAt < :cutoffDate")
    int deleteOldNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.user = :user AND n.createdAt < :cutoffDate")
    int deleteOldNotificationsByUser(@Param("user") User user, @Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM NotificationEntity n WHERE n.user = :user AND n.isRead = true")
    int deleteReadNotificationsByUser(@Param("user") User user);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true WHERE n.user = :user AND n.isRead = false")
    int markAllAsReadByUser(@Param("user") User user);

    long countByUserAndType(User user, NotificationType type);

    @Query("SELECT COUNT(n) FROM NotificationEntity n WHERE n.user = :user AND n.createdAt >= :since")
    long countRecentNotifications(@Param("user") User user, @Param("since") LocalDateTime since);

    @Query("SELECT n FROM NotificationEntity n WHERE n.user = :user ORDER BY n.createdAt DESC")
    List<NotificationEntity> findRecentNotifications(@Param("user") User user, Pageable pageable);

    @Query("""
        SELECT n.type, COUNT(n) 
        FROM NotificationEntity n 
        WHERE n.user = :user AND n.isRead = false 
        GROUP BY n.type
    """)
    List<Object[]> countUnreadNotificationsByType(@Param("user") User user);

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

    @Query("SELECT COUNT(DISTINCT n.user) FROM NotificationEntity n")
    long countDistinctUsers();

    long countByType(NotificationType type);

    long countByTypeAndCreatedAtBetween(NotificationType type, LocalDateTime start, LocalDateTime end);

    long countByTypeAndIsRead(NotificationType type, boolean isRead);

    long countByCreatedAtAfter(LocalDateTime after);

    long countByUser(User user);
}
