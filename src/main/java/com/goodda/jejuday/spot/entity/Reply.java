package com.goodda.jejuday.spot.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
// 승격 판정/화면 표시가 content_id 로 최상위 댓글 수를 센다. 인덱스가 없으면 풀 테이블 스캔이다 (EXPLAIN: type=ALL).
@Table(name = "reply", indexes = @Index(name = "idx_reply_content_depth", columnList = "content_id, depth"))
@Getter @Setter
public class Reply {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "content_id", nullable = false)
    private Long contentId; // Spot ID 또는 Community ID
    
    @Column(columnDefinition = "TINYINT DEFAULT 0")
    private Integer depth = 0;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_reply_id")
    private Reply parentReply;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Lob
    @Column(nullable = false)
    private String text;
    
    @Column(name = "is_deleted", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isDeleted = false;
    
    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}