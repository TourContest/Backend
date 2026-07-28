package com.goodda.jejuday.crawler.visitjeju.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class VisitJejuItem {
    private String contentsId;
    private String contentsCd;      // c1 관광지 / c5 축제·행사 ...
    private String title;
    private String introduction;
    private String address;
    private String roadAddress;
    private String tag;
    private String allTag;
    private Double latitude;
    private Double longitude;
    private String imgPath;
    private String thumbnailPath;

    public boolean isFestival() {
        return "c5".equals(contentsCd);
    }
}