package com.goodda.jejuday.spot.tourapi.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class TourItem {
    private String contentid;
    private String contenttypeid;
    private String title;

    private String addr1;
    private String addr2;
    private String zipcode;
    private String tel;
    private String firstimage;
    private String firstimage2;

    private String cat1; private String cat2; private String cat3;
    private String lclsSystm1;
    private String lclsSystm2;
    private String lclsSystm3;

    private String areacode;
    private String sigungucode;
    private String lDongRegnCd;
    private String lDongSignguCd;

    private String mapx;
    private String mapy;
    private String mlevel;
    private String showflag;

    private String createdtime;
    private String modifiedtime;
}