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
 * Spot 텍스트(name+categoryName+overview)의 임베딩 벡터.
 * MySQL엔 벡터 컬럼 타입이 없어 float[]를 JSON 배열 문자열로 직렬화해 저장하고,
 * 코사인 유사도는 애플리케이션 레벨에서 계산한다(반경 필터로 후보가 적어 충분히 빠름).
 * Spot에 대한 JPA 연관관계 객체는 일부러 두지 않는다 - 배치 동기화에서 항목 하나가 별도
 * 트랜잭션으로 저장되는데(외부 API 호출을 트랜잭션 밖에 두기 위해), detached/proxy 상태의
 * Spot을 연관관계에 물리면 "detached entity"/"uninitialized proxy" 예외가 난다.
 */
@Entity
@Table(name = "spot_embedding")
@Getter
@Setter
public class SpotEmbedding {

    @Id
    @Column(name = "spot_id")
    private Long spotId;

    @Lob
    @Column(name = "embedding_json", nullable = false, columnDefinition = "LONGTEXT")
    private String embeddingJson;

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
