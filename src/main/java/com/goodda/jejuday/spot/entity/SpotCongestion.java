package com.goodda.jejuday.spot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 날짜별 스팟 혼잡도. 외부 공공데이터(한국관광공사 방문자 빅데이터 등)의 실제 응답 단위가
 * 아직 검증 전이라 congestionScore는 0(한산)~1(매우 혼잡) 정규화 점수로 잡아둔다 - 실제 API 붙일 때
 * 원본 단위(방문자수/집중률 %)를 이 스케일로 변환하는 로직만 동기화 서비스에 추가하면 된다.
 * Spot에 대한 JPA 연관관계 객체는 두지 않는다(SpotDetail/SpotEmbedding과 동일한 이유 - 배치 동기화
 * 시 detached/uninitialized proxy 문제를 원천 차단).
 */
@Entity
@Table(name = "spot_congestion", uniqueConstraints = {
        @UniqueConstraint(name = "uk_spot_congestion_spot_date", columnNames = {"spot_id", "congestion_date"})
})
@Getter
@Setter
public class SpotCongestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spot_id", nullable = false)
    private Long spotId;

    @Column(name = "congestion_date", nullable = false)
    private LocalDate congestionDate;

    @Column(name = "congestion_score", nullable = false)
    private Double congestionScore;

    @Column(name = "external_score")
    private Double externalScore;

    @Column(name = "internal_score")
    private Double internalScore;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
