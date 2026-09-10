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

    // 공공데이터포털 문서(영문 페이지 파싱 기준) 상 areaCd가 필수 파라미터인데 지금까지
    // 안 보내고 있었다 - 지역 스코프 없이 호출하면 에러 없이 그냥 빈 목록만 돌아와서
    // (resultCode는 정상) 계속 0건이었던 것으로 보인다. 39=제주(표준 지역코드).
    // signguCd도 문서상 필수로 나오는데 정확한 제주시/서귀포시 값은 공식 매뉴얼(zip/이미지)에서만
    // 확인 가능해 아직 못 넣었다 - areaCd만 추가한 뒤 실제 응답(0건 여부)으로 추가 검증 필요.
    private static final String JEJU_AREA_CODE = "39";

    public List<JsonNode> congestionForecast(int pageNo, int rows) {
        MultiValueMap<String, String> q = base(pageNo, rows);
        q.add("areaCd", JEJU_AREA_CODE);
        return call(props.getCongestionServicePath() + "/tatsCnctrRatedList", q);
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
