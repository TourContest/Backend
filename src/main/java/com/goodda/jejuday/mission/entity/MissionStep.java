package com.goodda.jejuday.mission.entity;

import com.goodda.jejuday.spot.entity.Spot;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mission_step",
        indexes = {
                @Index(name = "ix_mission_step_theme", columnList = "mission_theme_id"),
                @Index(name = "ix_mission_step_spot", columnList = "spot_id")
        }
)
@Getter @Setter
public class MissionStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_theme_id", nullable = false)
    private MissionTheme missionTheme;

    // 기존 Spot 테이블 참조 (챌린지 완료 플로우를 그대로 재사용하기 위함)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot;

    // "order"는 SQL 예약어라 step_order로 매핑
    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "step_label", length = 100)
    private String stepLabel;
}
