package com.goodda.jejuday.spot.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "regional_visitor", uniqueConstraints = @UniqueConstraint(
        name = "uk_regional_visitor_date_code_level", columnNames = {"base_date", "region_code", "region_level"}))
@Getter @Setter
public class RegionalVisitor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;
    @Column(name = "region_code", length = 20, nullable = false)
    private String regionCode;
    @Column(name = "region_name", length = 100)
    private String regionName;
    @Column(name = "region_level", length = 20, nullable = false)
    private String regionLevel;
    @Column(name = "visitor_count")
    private Long visitorCount;
    @Column(name = "normalized_score")
    private Double normalizedScore;
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
