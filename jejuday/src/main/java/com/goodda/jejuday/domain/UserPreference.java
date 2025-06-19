package com.goodda.jejuday.domain;

import com.goodda.jejuday.Auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "UserPreference")
@Getter @Setter
public class UserPreference {
    @Id
    @Column(name = "user_id")
    private Long userId;
    
    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(nullable = false, length = 10, columnDefinition = "VARCHAR(10) DEFAULT 'ko'")
    private String language = "ko";
    
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('LIGHT','DARK') DEFAULT 'LIGHT'")
    private Theme theme = Theme.LIGHT;

    public enum Theme {
        LIGHT, DARK
    }
}