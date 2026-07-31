package com.goodda.jejuday.openai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodda.jejuday.openai.OpenAiProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 임베딩으로 추린 top-K 후보를 GPT로 최종 3개로 재정렬한다.
 * 사용자에게 보여줄 설명 문구는 필요 없고, spotId 순위만 JSON으로 받는다.
 * 실패/타임아웃 시 호출부가 임베딩 스코어 순 top3으로 폴백해야 한다.
 */
@Service
@RequiredArgsConstructor
public class OpenAiRerankService {

    private static final String SYSTEM_PROMPT =
            "당신은 여행 추천 랭킹 엔진입니다. 사용자의 선호 테마와 각 후보 장소 정보를 보고 "
            + "가장 적합한 3곳을 고른 뒤, spotId만 담은 JSON으로 답하세요. 오버투어리즘 완화가 목적이므로 "
            + "테마 적합도가 비슷하다면 혼잡도(congestion, 0=한산~1=매우혼잡)가 낮은 곳을 우선하세요. "
            + "형식: {\"rankedSpotIds\": [id1, id2, id3]}. 다른 설명은 절대 포함하지 마세요.";

    private final WebClient openAiWebClient;
    private final OpenAiProperties props;
    private final ObjectMapper objectMapper;

    public record RerankCandidate(Long spotId, String name, String category,
                                   String overviewSnippet, double distanceMeters, Double congestionScore) {}

    public List<Long> rerankTop3(List<String> userThemeNames, List<RerankCandidate> candidates) {
        String userPrompt = buildUserPrompt(userThemeNames, candidates);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getChatModel());
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)
        ));

        JsonNode response = openAiWebClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String content = response.path("choices").path(0).path("message").path("content").asText();
        try {
            JsonNode parsed = objectMapper.readTree(content);
            List<Long> ids = new ArrayList<>();
            for (JsonNode idNode : parsed.path("rankedSpotIds")) {
                ids.add(idNode.asLong());
            }
            return ids;
        } catch (Exception e) {
            throw new IllegalStateException("GPT rerank 응답 파싱 실패: " + content, e);
        }
    }

    private String buildUserPrompt(List<String> userThemeNames, List<RerankCandidate> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("사용자 선호 테마: ").append(String.join(", ", userThemeNames)).append("\n");
        sb.append("후보 목록:\n");
        for (RerankCandidate c : candidates) {
            sb.append("- spotId=").append(c.spotId())
                    .append(", 이름=").append(c.name())
                    .append(", 카테고리=").append(c.category() == null ? "" : c.category())
                    .append(", 거리=").append(Math.round(c.distanceMeters())).append("m")
                    .append(", 혼잡도=").append(c.congestionScore() == null ? "알수없음" : c.congestionScore())
                    .append(", 개요=").append(c.overviewSnippet() == null ? "" : c.overviewSnippet())
                    .append("\n");
        }
        return sb.toString();
    }
}
