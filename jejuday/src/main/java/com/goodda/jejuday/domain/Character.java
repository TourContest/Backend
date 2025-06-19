package com.goodda.jejuday.domain;

import com.goodda.jejuday.Auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "Character")
@Getter
@Setter
public class Character {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false, length = 50)
    private String name;
    
    private Integer level = 1;
    private Integer exp = 0;
    
    @Column(name = "daily_steps")
    private Integer dailySteps = 0;
    
    private Integer point = 0;
    private Integer intimacy = 0;
    
    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}