package com.goodda.jejuday.spot.entitiy;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "Likes") // 예약어 회피
@Getter
@Setter
public class Like {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;          // SPOT, REPLY, COMMUNITY
    
    @Column(name = "target_id", nullable = false)
    private Long targetId;
    
    @Column(name = "liked_at")
    @CreationTimestamp
    private LocalDateTime likedAt;

    public Like(Long userId, String spot, Long id) {
        this.user = new User(userId);
        this.user.setId(userId);
        this.targetType = TargetType.valueOf(spot.toUpperCase());
        this.targetId = id;
    }

    public enum TargetType {
        SPOT, REPLY, COMMUNITY
    }
}



//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "user_id", nullable = false)
//    private User user;
//
//    @Enumerated(EnumType.STRING)
//    @Column(name = "target_type", nullable = false)
//    private TargetType targetType;
//
//    @Column(name = "target_id", nullable = false)
//    private Long targetId;
//
//    @Column(name = "liked_at")
//    @CreationTimestamp
//    private LocalDateTime likedAt;
//
//    public enum TargetType {
//        SPOT, REPLY, COMMUNITY
