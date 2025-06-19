package com.goodda.jejuday.domain;

import com.goodda.jejuday.Auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "OAuth")
@Getter @Setter
public class OAuth {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, length = 50)
    private String provider;
    
    @Column(name = "provider_uid", nullable = false, length = 255)
    private String providerUid;
    
    @Column(name = "refresh_token", length = 255)
    private String refreshToken;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "linked_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime linkedAt;
}