package com.goodda.jejuday.spot.tourapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class TourApiPage {
    private final int pageNo;
    private final int numOfRows;
    private final int totalCount;
    private final List<TourItem> items;

    private TourApiPage(int pageNo, int numOfRows, int totalCount, List<TourItem> items) {
        this.pageNo = pageNo;
        this.numOfRows = numOfRows;
        this.totalCount = totalCount;
        this.items = items;
    }

    public static TourApiPage from(JsonNode root) {
        JsonNode response = root.path("response");
        String code = response.path("header").path("resultCode").asText("");
        if (!"0000".equals(code)) throw new IllegalStateException("TourAPI error: " + code);

        JsonNode body = response.path("body");
        int pageNo = body.path("pageNo").asInt(1);
        int numOfRows = body.path("numOfRows").asInt(0);
        int totalCount = body.path("totalCount").asInt(0);

        List<TourItem> items = new ArrayList<>();
        JsonNode itemsNode = body.path("items");
        if (!(itemsNode.isTextual() && itemsNode.asText().isBlank())) {
            JsonNode itemNode = itemsNode.path("item");
            if (itemNode.isArray()) for (JsonNode n : itemNode) items.add(readItem(n));
            else if (!itemNode.isMissingNode()) items.add(readItem(itemNode));
        }
        return new TourApiPage(pageNo, numOfRows, totalCount, items);
    }

    private static TourItem readItem(JsonNode n) {
        TourItem t = new TourItem();
        t.setContentid(n.path("contentid").asText(null));
        t.setContenttypeid(n.path("contenttypeid").asText(null));
        t.setTitle(n.path("title").asText(null));

        t.setAddr1(n.path("addr1").asText(null));
        t.setAddr2(n.path("addr2").asText(null));
        t.setZipcode(n.path("zipcode").asText(null));
        t.setTel(n.path("tel").asText(null));
        t.setFirstimage(n.path("firstimage").asText(null));
        t.setFirstimage2(n.path("firstimage2").asText(null));

        t.setCat1(n.path("cat1").asText(null));
        t.setCat2(n.path("cat2").asText(null));
        t.setCat3(n.path("cat3").asText(null));
        t.setLclsSystm1(n.path("lclsSystm1").asText(null));
        t.setLclsSystm2(n.path("lclsSystm2").asText(null));
        t.setLclsSystm3(n.path("lclsSystm3").asText(null));

        t.setAreacode(n.path("areacode").asText(null));
        t.setSigungucode(n.path("sigungucode").asText(null));
        t.setLDongRegnCd(n.path("lDongRegnCd").asText(null));
        t.setLDongSignguCd(n.path("lDongSignguCd").asText(null));

        t.setMapx(n.path("mapx").asText(null));
        t.setMapy(n.path("mapy").asText(null));
        t.setMlevel(n.path("mlevel").asText(null));
        t.setShowflag(n.path("showflag").asText(null));

        t.setCreatedtime(n.path("createdtime").asText(null));
        t.setModifiedtime(n.path("modifiedtime").asText(null));
        return t;
    }
}