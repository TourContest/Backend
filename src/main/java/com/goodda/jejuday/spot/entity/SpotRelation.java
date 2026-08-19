package com.goodda.jejuday.spot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "spot_relation", uniqueConstraints = @UniqueConstraint(
        name = "uk_spot_relation_source_target_type", columnNames = {"source_spot_id", "target_spot_id", "relation_type"}))
@Getter @Setter
public class SpotRelation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "source_spot_id", nullable = false)
    private Long sourceSpotId;
    @Column(name = "target_spot_id", nullable = false)
    private Long targetSpotId;
    @Column(name = "relation_type", length = 30, nullable = false)
    private String relationType = "ALL";
    @Column(name = "relation_rank")
    private Integer relationRank;
    @Column(name = "relation_score")
    private Double relationScore;
    @Column(name = "source_period", length = 6)
    private String sourcePeriod;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
