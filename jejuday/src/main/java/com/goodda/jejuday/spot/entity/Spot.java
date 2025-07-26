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
@Table(name = "Spot")
@Getter @Setter
public class Spot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpotType type;
    
    @Column(length = 200)
    private String name;
    
    @Lob
    private String description;

    @Column(length = 200)
    private BigDecimal latitude;

    @Column(length = 200)
    private BigDecimal  longitude;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_spot_id")
    private Spot parentSpot;
    
    @Column(name = "start_date")
    private LocalDate startDate;
    
    @Column(name = "end_date")
    private LocalDate endDate;
    
    private Integer point = 0;
    
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
}