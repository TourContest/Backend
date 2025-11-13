package com.goodda.jejuday.spot.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "spot_likes")  // “Likes” 예약어 회피
@Getter
@Setter
public class Like {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // spot 과 like 의 중계 테이블,
    // 누가 좋아요를 눌렀는지에 대한.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    // 어느 spot 에 눌렀는지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot; // 좋아요가 달린 Spot 엔티티

    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;          // SPOT, REPLY, COMMUNITY
    
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    // 눌른 시각
    @Column(name = "liked_at")
    @CreationTimestamp
    private LocalDateTime likedAt;


//    public Like(User user, Spot spot, Long id) {
//        this.user = user;
//        this.spot = spot;
////        this.targetType = TargetType.valueOf(spot.toUpperCase()); // 얘는 뭘까?
//        this.targetId = id;
//    }

    public Like(User user, Spot spot, TargetType targetType) {
        this.user = user;
        this.spot = spot;
        this.targetType = targetType;
        this.targetId = spot.getId();
    }

    public Like(User user, Spot spot) {
        this.user = user;
        this.spot = spot;
    }

    public Like() {
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
