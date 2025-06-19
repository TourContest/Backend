package com.goodda.jejuday.domain;

import com.goodda.jejuday.Auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "ChattingRoomMember")
@Getter @Setter
public class ChattingRoomMember {
    @EmbeddedId
    private ChattingRoomMemberId id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roomId")
    @JoinColumn(name = "room_id")
    private ChattingRoom room;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(name = "joined_at")
    @CreationTimestamp
    private LocalDateTime joinedAt;

    @Embeddable
    @Getter @Setter
    public static class ChattingRoomMemberId implements Serializable {
        private Long roomId;
        private Long userId;
        
        // equals(), hashCode() 구현
    }
}