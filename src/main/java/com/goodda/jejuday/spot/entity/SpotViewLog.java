package com.goodda.jejuday.spot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "spot_view_log")
@Getter @Setter @NoArgsConstructor
public class SpotViewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어느 Spot 조회인지
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="spot_id", foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Spot spot;

    // 로그인 유저일 때
    @Column(name = "user_id")
    private Long userId;

    // 비로그인 세션 식별용
    @Column(name = "session_id", length = 64)
    private String sessionId;

    // 조회 시각
    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    // 나중에 로그인 하지 않아도 조회가 가능하게 변경 할때 필요
    //    private String sessionId;      // 익명 조회 시에도 기록 가능
}
