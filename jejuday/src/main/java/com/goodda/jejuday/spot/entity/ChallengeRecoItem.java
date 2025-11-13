package com.goodda.jejuday.spot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "challenge_reco_item")
@Getter
@Setter
public class ChallengeRecoItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable = false)
    private Long userId;

    @Column(name="slot_index", nullable = false)
    private Integer slotIndex; // 0..3

    @Column(name="spot_id", nullable = false)
    private Long spotId;

    @Column(name="theme_id")
    private Long themeId;      // null 허용

    @Column(name="reason", length = 20, nullable = false)
    private String reason;     // "THEME_MATCH" | "RANDOM_FILL"

    @Column(name="generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name="expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
