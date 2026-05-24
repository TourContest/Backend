package com.goodda.jejuday.spot.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.goodda.jejuday.spot.tourapi.dto.TourApiPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

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

    private MultiValueMap<String, String> baseParams(String arrange, String areaCode,
                                                     String lDongRegnCd, String lDongSignguCd,
                                                     int pageNo, int rows) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("serviceKey", props.getServiceKey());
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