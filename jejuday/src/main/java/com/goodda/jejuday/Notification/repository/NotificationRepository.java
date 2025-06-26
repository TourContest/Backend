package com.goodda.jejuday.Notification.repository;

import com.goodda.jejuday.Auth.entity.User;
import com.goodda.jejuday.Notification.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findAllByUserOrderByCreatedAtDesc(User user);
}