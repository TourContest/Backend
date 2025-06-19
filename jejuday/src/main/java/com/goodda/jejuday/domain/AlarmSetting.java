package com.goodda.jejuday.domain;

import com.goodda.jejuday.Auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "AlarmSetting")
@Getter
@Setter
public class AlarmSetting {
    @Id
    @Column(name = "user_id")
    private Long userId;
    
    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(name = "notify_reply", columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean notifyReply = true;
    
    @Column(name = "notify_reply_to_me", columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean notifyReplyToMe = true;
    
    @Column(name = "notify_follow", columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean notifyFollow = true;
    
    @Column(name = "notify_notice", columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean notifyNotice = true;
}