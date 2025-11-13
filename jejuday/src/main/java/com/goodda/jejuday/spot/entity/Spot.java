package com.goodda.jejuday.spot.entity;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.entity.UserTheme;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.security.InvalidParameterException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // 메인 테마, user_theme 고정된 튜플 10개중에서 선택 -> 선택을 안 해도 되나?
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "theme_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_spot_theme"))
    private UserTheme theme;

    @Column(name = "tag1", length = 50)
    private String tag1;

    @Column(name = "tag2", length = 50)
    private String tag2;

    @Column(name = "tag3", length = 50)
    private String tag3;


    // 작성 주체: true = 유저가 작성, false = 운영자 작성 or 네이버 지도 API 호출
    @Column(name = "is_user_created")
    private boolean userCreated;

    @Column(length = 512)
    private String img1;

    @Column(length = 512)
    private String img2;

    @Column(length = 512)
    private String img3;

    // 가연님 요구사항 : 상호명, 카테고리 그룹 코드, 카테고리 그룹 네임, 카테고리 이름, 아이디, 주소지, 위도,경도 속성으로 추가
    // 위도 경도 아이디 이미 있음. 상호명은 Name 으로
    // 없는거 : 카테고리 그룹 코드, 카테고리 그룹 네임, 카테고리 이름, 아이디는 아마 스팟 아이디,

    // ====== [추가] 외부 장소/카테고리 메타 ======
    // 네이버 지도 등 외부 제공처의 장소 고유 ID (충돌 방지를 위해 unique 미설정, 인덱스만)
    @Column(name = "external_place_id", length = 100)
    private String externalPlaceId;

    // 카테고리 그룹 코드(예: 대분류 코드)
    @Column(name = "category_group_code", length = 50)
    private String categoryGroupCode;

    // 카테고리 그룹 이름(예: 대분류 명)
    @Column(name = "category_group_name", length = 100)
    private String categoryGroupName;

    // 카테고리 이름(예: 소분류 명)
    @Column(name = "category_name", length = 100)
    private String categoryName;
    // ========================================

    // 편의 메서드
    public List<String> getImageUrls() {
        List<String> urls = new ArrayList<>(3);
        if (img1 != null && !img1.isBlank()) urls.add(img1);
        if (img2 != null && !img2.isBlank()) urls.add(img2);
        if (img3 != null && !img3.isBlank()) urls.add(img3);
        return urls;
    }

    public void setImagesOrdered(List<String> urls) {
        this.img1 = null; this.img2 = null; this.img3 = null;
        if (urls == null) return;
        if (urls.size() > 0) this.img1 = urls.get(0);
        if (urls.size() > 1) this.img2 = urls.get(1);
        if (urls.size() > 2) this.img3 = urls.get(2);
    }


}

