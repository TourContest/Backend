package com.goodda.jejuday.crawler.visitjeju;

import com.fasterxml.jackson.databind.JsonNode;
import com.goodda.jejuday.crawler.visitjeju.dto.VisitJejuPage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class VisitJejuClient {

    private final VisitJejuProperties props;
    private final WebClient visitJejuWebClient;

    public VisitJejuClient(VisitJejuProperties props,
                           @Qualifier("visitJejuWebClient") WebClient visitJejuWebClient) {
        this.props = props;
        this.visitJejuWebClient = visitJejuWebClient;
    }

    /**
     * 콘텐츠 목록 조회.
     * category·cid 파라미터로 카테고리 필터가 동작하지 않아
     * 전량 조회 후 호출 측에서 contentscd 로 걸러낸다.
     */
    public VisitJejuPage searchList(int page) {
        MultiValueMap<String, String> q = new LinkedMultiValueMap<>();
        q.add("apiKey", props.getApiKey());
        q.add("locale", "kr");
        q.add("page", String.valueOf(page));

        JsonNode body = visitJejuWebClient.get()
                .uri(uri -> uri.path("/vsjApi/contents/searchList").queryParams(q).build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        return VisitJejuPage.from(body);
    }
}