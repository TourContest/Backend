package com.goodda.jejuday.spot.dto;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.spot.entity.Spot;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class SpotResponse {
    private Long id;
    private String name;
    private String title;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private int likeCount;
    private boolean likedByMe;
    private List<String> imageUrls;

    // 작성자 정보 추가
    private Long userId;
    private String userNickname;
    private String userProfile;

    private Spot.SpotType type;
    private boolean challengeOngoing;

    private LocalDateTime createdAt; // 추가: 작성 시간

    // 공공데이터(TourAPI) 동기화로 생성된 항목인지 여부. Spot.userCreated를 그대로 반영 —
    // 작성자 userId 비교는 시스템 계정과 실제 유저 계정이 같은 id를 쓰는 환경도 있어 신뢰 불가.
    // 승격(POST->SPOT->CHALLENGE)된 유저 게시글은 userCreated가 유지되므로 isOfficial=false로 남는다.
    private boolean isOfficial;

    public static SpotResponse fromEntity(Spot spot, int likeCount, boolean likedByMe) {
        List<String> imgs = new ArrayList<>(3);
        if (spot.getImg1() != null && !spot.getImg1().isBlank()) imgs.add(spot.getImg1());
        if (spot.getImg2() != null && !spot.getImg2().isBlank()) imgs.add(spot.getImg2());
        if (spot.getImg3() != null && !spot.getImg3().isBlank()) imgs.add(spot.getImg3());

        // 진행중 챌린지 여부 계산 (Spot.type == CHALLENGE && 오늘이 기간 안)
        boolean ongoing = false;
        if (spot.getType() == Spot.SpotType.CHALLENGE) {
            LocalDate today = LocalDate.now();
            if (spot.getStartDate() != null && spot.getEndDate() != null
                    && !today.isBefore(spot.getStartDate())
                    && !today.isAfter(spot.getEndDate())) {
                ongoing = true;
            }
        }

        // 공공데이터 동기화 스팟 중 일부는 작성자(user)가 비어있을 수 있어 방어적으로 처리
        User author = spot.getUser();
        boolean isOfficial = !spot.isUserCreated();

        return new SpotResponse(
                spot.getId(),
                spot.getName(),
                spot.getTitle(),
                spot.getDescription(), // 글 내용
                spot.getLatitude(),
                spot.getLongitude(),
                likeCount,
                likedByMe,
                imgs,
                author != null ? author.getId() : null, // 작성자 ID
                author != null ? author.getNickname() : "제주데이", // 작성자 닉네임
                author != null ? author.getProfile() : null, // 작성자 프로필
                spot.getType(),
                ongoing,
                spot.getCreatedAt(), // 작성 시간
                isOfficial
        );
    }
}