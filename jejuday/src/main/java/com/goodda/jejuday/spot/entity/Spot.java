package com.goodda.jejuday.spot.entity;

import com.goodda.jejuday.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.security.InvalidParameterException;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "spot")
@Getter @Setter
public class Spot {

    //
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpotType type;

    // 제목
    @Column(length = 200)
    private String name;
    
    @Lob
    private String description;

    // @Column(nullable = false, precision = 10, scale = 7) // 정밀도 필요하실 채택, precision : 전체 몇자리, scale : 소수점 몇자리
    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    // @Column(nullable = false, precision = 10, scale = 7)
    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal  longitude;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 누적 조회 수 (denormalized)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer viewCount = 0;

    // 누적 좋아요 수 (denormalized)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer likeCount = 0;

    //
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "point", columnDefinition = "INT DEFAULT 0")
    private Integer point = 0;  // user 의 한라봉과 외래키 관계.

    @Column(name = "is_deleted", columnDefinition = "TINYINT(1) DEFAULT 0")
    private Boolean isDeleted = false;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @Column(name = "deleted_by")
    private Long deletedBy;
    
    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Spot(Long id) {
        this.id = id;
    }

    public Spot(String name, String description, BigDecimal latitude, BigDecimal longitude, User user) {
        this.name = name;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.user = user;
        this.type = SpotType.POST; // 기본값으로 POST
    }

    public Spot() {
    }

    public enum SpotType {          // 유저가 올린 스팟(POST) -> 지도에 위치마커 띄울 스팟(SPOT) -> 챌린저(CHALLENGE)
        POST, SPOT, CHALLENGE
    }

    // 작성 주체: true = 유저가 작성, false = 운영자 작성
    @Column(name = "is_user_created")
    private boolean userCreated;


    // TODO 가연님 요구사항 : 상호명, 카테고리 그룹 코드, 카테고리 그룹 네임, 카테고리 이름, 아이디, 주소지, 위도,경도 속성으로 추가
    // 위도 경도 아이디 이미 있음.
    // 상호명 : Name

    //
}