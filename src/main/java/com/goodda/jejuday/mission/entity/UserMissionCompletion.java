package com.goodda.jejuday.mission.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_mission_completion",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_mission_completion", columnNames = {"user_id", "mission_theme_id"}),
        indexes = @Index(name = "ix_umc_user", columnList = "user_id")
)
@Getter @Setter
public class UserMissionCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_theme_id", nullable = false)
    private MissionTheme missionTheme;

    @Column(name = "hallabong_awarded", nullable = false)
    private int hallabongAwarded;

    @CreationTimestamp
    @Column(name = "completed_at", updatable = false)
    private LocalDateTime completedAt;
}
