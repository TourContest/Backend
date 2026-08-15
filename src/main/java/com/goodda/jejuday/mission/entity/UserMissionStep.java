package com.goodda.jejuday.mission.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_mission_step",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_mission_step", columnNames = {"user_id", "mission_step_id"}),
        indexes = @Index(name = "ix_ums_user", columnList = "user_id")
)
@Getter @Setter
public class UserMissionStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_step_id", nullable = false)
    private MissionStep missionStep;

    @CreationTimestamp
    @Column(name = "completed_at", updatable = false)
    private LocalDateTime completedAt;
}
