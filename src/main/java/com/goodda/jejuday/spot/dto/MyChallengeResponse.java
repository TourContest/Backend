package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.spot.entity.ChallengeParticipation;
import com.goodda.jejuday.spot.entity.Spot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyChallengeResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String img1; // 추가
    private Long themeId;
    private String themeName;
    private Integer point;
    private Integer viewCount;
    private Integer likeCount;
    
    // 참여 정보
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime joinedAt;
    private LocalDateTime completedAt;

    public static MyChallengeResponse of(Spot spot, ChallengeParticipation participation) {
        if (spot == null || participation == null) return null;
        
        return new MyChallengeResponse(
                spot.getId(),
                spot.getName(),
                spot.getDescription(),
                spot.getLatitude(),
                spot.getLongitude(),
                spot.getImg1(), // img1 추가
                spot.getTheme() != null ? spot.getTheme().getId() : null,
                spot.getTheme() != null ? spot.getTheme().getName() : null,
                spot.getPoint(),
                spot.getViewCount(),
                spot.getLikeCount(),
                participation.getStatus().name(),
                participation.getStartDate(),
                participation.getEndDate(),
                participation.getJoinedAt(),
                participation.getCompletedAt()
        );
    }
}