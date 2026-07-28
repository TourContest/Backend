package com.goodda.jejuday.crawler.visitjeju.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class VisitJejuPage {

    private final List<VisitJejuItem> items = new ArrayList<>();
    private int totalCount;
    private int pageCount;
    private int currentPage;

    public static VisitJejuPage from(JsonNode root) {
        VisitJejuPage page = new VisitJejuPage();
        if (root == null) return page;

        page.totalCount = root.path("totalCount").asInt(0);
        page.pageCount = root.path("pageCount").asInt(0);
        page.currentPage = root.path("currentPage").asInt(0);

        JsonNode items = root.path("items");
        if (!items.isArray()) return page;   // 결과 없을 때 빈 배열/문자열 방어

        for (JsonNode n : items) {
            page.items.add(readItem(n));
        }
        return page;
    }

    private static VisitJejuItem readItem(JsonNode n) {
        VisitJejuItem it = new VisitJejuItem();
        it.setContentsId(text(n, "contentsid"));
        it.setContentsCd(text(n.path("contentscd"), "value"));
        it.setTitle(text(n, "title"));
        it.setIntroduction(text(n, "introduction"));
        it.setAddress(text(n, "address"));
        it.setRoadAddress(text(n, "roadaddress"));
        it.setTag(text(n, "tag"));
        it.setAllTag(text(n, "alltag"));

        if (n.hasNonNull("latitude")) it.setLatitude(n.path("latitude").asDouble());
        if (n.hasNonNull("longitude")) it.setLongitude(n.path("longitude").asDouble());

        JsonNode photo = n.path("repPhoto").path("photoid");
        it.setImgPath(text(photo, "imgpath"));
        it.setThumbnailPath(text(photo, "thumbnailpath"));
        return it;
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return (v == null || v.isBlank() || "null".equals(v)) ? null : v;
    }
}