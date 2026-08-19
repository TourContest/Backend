package com.goodda.jejuday.spot.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

/** 관광공사 데이터랩 계열 API의 원본 행을 공통 형태로 수집한다. */
@Component
@RequiredArgsConstructor
public class TourDataClient {
    private final WebClient tourWebClient;
    private final TourApiProperties props;

    public List<JsonNode> congestionForecast(int pageNo, int rows) {
        return call(props.getCongestionServicePath() + "/tatsCnctrRatedList",
                base(pageNo, rows));
    }

    public List<JsonNode> relatedTour(String areaCd, String signguCd, int pageNo, int rows) {
        MultiValueMap<String, String> q = base(pageNo, rows);
        if (props.getRelatedBaseYm() != null && !props.getRelatedBaseYm().isBlank()) q.add("baseYm", props.getRelatedBaseYm());
        if (areaCd != null) q.add("areaCd", areaCd);
        if (signguCd != null) q.add("signguCd", signguCd);
        return call(props.getRelatedServicePath() + "/areaBasedList1", q);
    }

    public List<JsonNode> regionalVisitors(boolean local, LocalDate start, LocalDate end, int pageNo, int rows) {
        MultiValueMap<String, String> q = base(pageNo, rows);
        DateTimeFormatter f = DateTimeFormatter.BASIC_ISO_DATE;
        q.add("startYmd", start.format(f));
        q.add("endYmd", end.format(f));
        String endpoint = local ? "/locgoRegnVisitrDDList" : "/metcoRegnVisitrDDList";
        return call(props.getDataLabServicePath() + endpoint, q);
    }

    private MultiValueMap<String, String> base(int pageNo, int rows) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("serviceKey", props.getEncodedServiceKey());
        q.add("MobileOS", "ETC");
        q.add("MobileApp", "JejuDay");
        q.add("_type", "json");
        q.add("pageNo", String.valueOf(pageNo));
        q.add("numOfRows", String.valueOf(rows));
        return q;
    }

    private List<JsonNode> call(String path, MultiValueMap<String, String> query) {
        JsonNode root = tourWebClient.get().uri(UriComponentsBuilder.fromUriString(props.getBaseUrl())
                        .path(path).queryParams(query).build(true).toUri())
                .retrieve().bodyToMono(JsonNode.class).block();
        if (root == null) return List.of();
        String code = root.path("response").path("header").path("resultCode").asText("");
        if (!code.isBlank() && !"0000".equals(code)) {
            throw new IllegalStateException("Tour data API error: " + code + " "
                    + root.path("response").path("header").path("resultMsg").asText(""));
        }
        JsonNode item = root.path("response").path("body").path("items").path("item");
        if (item.isMissingNode() || item.isNull() || (item.isTextual() && item.asText().isBlank())) return List.of();
        List<JsonNode> result = new ArrayList<>();
        if (item.isArray()) item.forEach(result::add); else result.add(item);
        return result;
    }
}
