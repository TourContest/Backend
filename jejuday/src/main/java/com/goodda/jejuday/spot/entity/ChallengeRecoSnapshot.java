package com.goodda.jejuday.spot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "challenge_reco_snapshot")
@Getter
@Setter
public class ChallengeRecoSnapshot {
  @Id
  @Column(name="user_id") private Long userId;

  @Lob
  @Column(name="spot_ids_json", nullable=false)
  private String spotIdsJson;

  @Column(name="generated_at", nullable=false)
  private LocalDateTime generatedAt;

  @Column(name="expires_at", nullable=false)
  private LocalDateTime expiresAt;

  @Column(name="is_dirty", nullable=false)
  private boolean dirty = false;

  @Column(name="source_ver")
  private String sourceVer;
}