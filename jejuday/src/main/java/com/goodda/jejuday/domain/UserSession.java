package com.goodda.jejuday.domain;

import com.goodda.jejuday.Auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "UserSession")
@Getter @Setter
public class UserSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "session_token", nullable = false, unique = true, length = 255)
    private String sessionToken;
    
    @Column(name = "device_info", length = 255)
    private String deviceInfo;
    
    @Column(name = "login_ip", length = 45)
    private String loginIp;
    
    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}