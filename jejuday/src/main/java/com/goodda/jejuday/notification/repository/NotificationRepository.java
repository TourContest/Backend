package com.goodda.jejuday.notification.repository;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.notification.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    List<NotificationEntity> findAllByUserOrderByCreatedAtDesc(User user);
}