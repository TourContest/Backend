package com.goodda.jejuday.spot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * TourAPI detailCommon2/detailIntro2/detailImage2로 보강한 Spot 상세정보 (Spot과 1:1 관계, PK=FK 공유).
 * Spot에 대한 JPA 연관관계 객체는 일부러 두지 않는다 - 배치 동기화에서 항목 하나가 별도 트랜잭션으로
 * 저장되는데(외부 API 호출을 트랜잭션 밖에 두기 위해), detached/proxy 상태의 Spot을 연관관계에 물리면
 * "detached entity"/"uninitialized proxy" 예외가 난다. spotId만 직접 다루면 이 문제가 아예 없다.
 */
@Entity
@Table(name = "spot_detail")
@Getter
@Setter
public class SpotDetail {

    @Id
    @Column(name = "spot_id")
    private Long spotId;

    @Lob
    @Column(name = "overview", columnDefinition = "LONGTEXT")
    private String overview;

    @Column(name = "homepage", length = 300)
    private String homepage;

    @Column(name = "use_time", length = 500)
    private String useTime;

    @Column(name = "rest_date", length = 200)
    private String restDate;

    @Column(name = "parking", length = 300)
    private String parking;

    @Lob
    @Column(name = "extra_images_json", columnDefinition = "LONGTEXT")
    private String extraImagesJson;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;
}
