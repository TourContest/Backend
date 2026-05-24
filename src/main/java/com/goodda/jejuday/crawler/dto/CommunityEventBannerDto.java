package com.goodda.jejuday.crawler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.goodda.jejuday.crawler.entitiy.JejuEvent;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommunityEventBannerDto {
    private Long id;
    private String title;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    @JsonProperty("image_url")
    private String imageUrl;
    private String detailUrl;  // 클릭 시 이동

    public static CommunityEventBannerDto from(JejuEvent e) {
        return CommunityEventBannerDto.builder()
                .id(e.getId())
                .title(e.getTitle())
                .location(e.getLocation())
                .startDate(e.getPeriodStart())
                .endDate(e.getPeriodEnd())
                .detailUrl(e.getDetailUrl())
                .imageUrl(e.getImageUrl())
                .build();
    }
}
