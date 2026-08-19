package com.goodda.jejuday.spot.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.goodda.jejuday.spot.tourapi.dto.TourApiPage;
import com.goodda.jejuday.spot.tourapi.dto.TourDetailCommon;
import com.goodda.jejuday.spot.tourapi.dto.TourDetailIntro;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TourApiClient {

    private final WebClient tourWebClient;
    private final TourApiProperties props;

    public TourApiPage areaBasedList(int pageNo, int rows, String arrange,
                                     String areaCode, String lDongRegnCd, String lDongSignguCd) {
        MultiValueMap<String, String> q = baseParams(arrange, areaCode, lDongRegnCd, lDongSignguCd, pageNo, rows);
        String path = props.getKorServicePath() + "/areaBasedList2";
        JsonNode body = tourWebClient.get().uri(uri -> uri.path(path).queryParams(q).build())
                .retrieve().bodyToMono(JsonNode.class).block();
        return TourApiPage.from(body);
    }

    /** 사용자 현재 위치 주변 관광정보. radius는 TourAPI 제한에 맞춰 최대 20km로 제한한다. */
    public TourApiPage locationBasedList(int pageNo, int rows, String arrange,
                                         String mapX, String mapY, int radiusMeters) {
        MultiValueMap<String, String> q = commonParams(pageNo, rows);
        q.add("mapX", mapX);
        q.add("mapY", mapY);
        q.add("radius", String.valueOf(Math.max(1, Math.min(radiusMeters, 20_000))));
        if (arrange != null) q.add("arrange", arrange);
        String path = props.getKorServicePath() + "/locationBasedList2";
        JsonNode body = tourWebClient.get().uri(uri -> uri.path(path).queryParams(q).build())
                .retrieve().bodyToMono(JsonNode.class).block();
        return TourApiPage.from(body);
    }

    public TourApiPage areaBasedSyncList(String sinceYmd, int pageNo, int rows, String arrange,
                                         String areaCode, String lDongRegnCd, String lDongSignguCd,
                                         String oldContentId) {
        MultiValueMap<String, String> q = baseParams(arrange, areaCode, lDongRegnCd, lDongSignguCd, pageNo, rows);
        q.add("modifiedtime", sinceYmd);
        if (oldContentId != null) q.add("oldContentid", oldContentId);
        String path = props.getKorServicePath() + "/areaBasedSyncList2";
        JsonNode body = tourWebClient.get().uri(uri -> uri.path(path).queryParams(q).build())
                .retrieve().bodyToMono(JsonNode.class).block();
        return TourApiPage.from(body);
    }

    /**
     * 축제·행사 목록 조회 (searchFestival2, contenttypeid=15 고정).
     * eventStartYmd 이후 시작하는 행사를 반환한다.
     */
    public TourApiPage searchFestival(String eventStartYmd, int pageNo, int rows, String areaCode) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("serviceKey", props.getNormalizedServiceKey());
        q.add("MobileOS", "ETC");
        q.add("MobileApp", "JejuDay");
        q.add("_type", "json");
        q.add("arrange", "A");
        q.add("eventStartDate", eventStartYmd);
        if (areaCode != null) q.add("areaCode", areaCode);
        q.add("numOfRows", String.valueOf(rows));
        q.add("pageNo", String.valueOf(pageNo));

        String path = props.getKorServicePath() + "/searchFestival2";
        JsonNode body = tourWebClient.get().uri(uri -> uri.path(path).queryParams(q).build())
                .retrieve().bodyToMono(JsonNode.class).block();
        return TourApiPage.from(body);
    }

    /** 공통정보 조회 (개요/홈페이지). 주의: 이 TourAPI 배포본은 defaultYN 등 필터 플래그를 거부하므로 붙이지 않는다. */
    public TourDetailCommon detailCommon(String contentId) {
        MultiValueMap<String, String> q = detailBaseParams(contentId);
        String path = props.getKorServicePath() + "/detailCommon2";
        JsonNode body = tourWebClient.get().uri(uri -> uri.path(path).queryParams(q).build())
                .retrieve().bodyToMono(JsonNode.class).block();
        return TourDetailCommon.from(body);
    }

    /** 소개정보 조회 (이용시간/휴무일/주차 등, contentTypeId 필수) */
    public TourDetailIntro detailIntro(String contentId, String contentTypeId) {
        MultiValueMap<String, String> q = detailBaseParams(contentId);
        q.add("contentTypeId", contentTypeId);
        String path = props.getKorServicePath() + "/detailIntro2";
        JsonNode body = tourWebClient.get().uri(uri -> uri.path(path).queryParams(q).build())
                .retrieve().bodyToMono(JsonNode.class).block();
        return TourDetailIntro.from(body);
    }

    /** 추가 이미지 목록 조회. 주의: 이 TourAPI 배포본은 imageYN/subImageYN 플래그를 거부하므로 붙이지 않는다. */
    public List<String> detailImage(String contentId) {
        MultiValueMap<String, String> q = detailBaseParams(contentId);
        String path = props.getKorServicePath() + "/detailImage2";
        JsonNode body = tourWebClient.get().uri(uri -> uri.path(path).queryParams(q).build())
                .retrieve().bodyToMono(JsonNode.class).block();

        String code = body.path("response").path("header").path("resultCode").asText("");
        if (!"0000".equals(code)) {
            throw new IllegalStateException("TourAPI detailImage2 error: " + code + " "
                    + body.path("response").path("header").path("resultMsg").asText(""));
        }

        List<String> urls = new ArrayList<>();
        JsonNode itemNode = body.path("response").path("body").path("items").path("item");
        if (itemNode.isArray()) {
            for (JsonNode n : itemNode) {
                String url = n.path("originimgurl").asText(null);
                if (url != null && !url.isBlank()) urls.add(url);
            }
        } else if (!itemNode.isMissingNode()) {
            String url = itemNode.path("originimgurl").asText(null);
            if (url != null && !url.isBlank()) urls.add(url);
        }
        return urls;
    }

    private MultiValueMap<String, String> detailBaseParams(String contentId) {
        MultiValueMap<String, String> q = commonParams(null, null);
        q.add("contentId", contentId);
        return q;
    }

    private MultiValueMap<String, String> commonParams(Integer pageNo, Integer rows) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("serviceKey", props.getNormalizedServiceKey());
        q.add("MobileOS", "ETC");
        q.add("MobileApp", "JejuDay");
        q.add("_type", "json");
        if (rows != null) q.add("numOfRows", String.valueOf(rows));
        if (pageNo != null) q.add("pageNo", String.valueOf(pageNo));
        return q;
    }

    private MultiValueMap<String, String> baseParams(String arrange, String areaCode,
                                                     String lDongRegnCd, String lDongSignguCd,
                                                     int pageNo, int rows) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("serviceKey", props.getNormalizedServiceKey());
        q.add("MobileOS", "ETC");
        q.add("MobileApp", "JejuDay");
        q.add("_type", "json");
        if (arrange != null) q.add("arrange", arrange);

        if (lDongRegnCd != null) q.add("lDongRegnCd", lDongRegnCd);
        if (lDongSignguCd != null) q.add("lDongSignguCd", lDongSignguCd);
        if (lDongRegnCd == null && areaCode != null) q.add("areaCode", areaCode);

        q.add("numOfRows", String.valueOf(rows));
        q.add("pageNo", String.valueOf(pageNo));
        return q;
    }
}
