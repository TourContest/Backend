package com.goodda.jejuday.domain;

import com.goodda.jejuday.Auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "Community")
@Getter @Setter
public class Community {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(length = 50, columnDefinition = "VARCHAR(50) DEFAULT 'FREE'")
    private String category;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Lob
    @Column(nullable = false)
    private String content;
    
    private Integer views = 0;
    

    
    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}