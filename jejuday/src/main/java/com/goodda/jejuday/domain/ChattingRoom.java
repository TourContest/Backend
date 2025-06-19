package com.goodda.jejuday.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ChattingRoom")
@Getter @Setter
public class ChattingRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;
    
    @Column(name = "max_participants")
    private Integer maxParticipants;
    
    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
}