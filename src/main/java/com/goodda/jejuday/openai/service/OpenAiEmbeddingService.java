package com.goodda.jejuday.openai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.goodda.jejuday.openai.OpenAiProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OpenAI Embeddings API 호출. 실패 시 예외를 그대로 던지므로,
 * 실시간 추천 경로 호출부에서 catch해서 거리 전용 정렬 등으로 폴백해야 한다.
 */
@Service
@RequiredArgsConstructor
public class OpenAiEmbeddingService {

    private final WebClient openAiWebClient;
    private final OpenAiProperties props;

    public float[] embed(String text) {
        List<float[]> result = embedBatch(List.of(text));
        return result.get(0);
    }

    public List<float[]> embedBatch(List<String> texts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getEmbeddingModel());
        body.put("input", texts);

        JsonNode response = openAiWebClient.post()
                .uri("/v1/embeddings")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        JsonNode data = response.path("data");
        List<float[]> vectors = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            JsonNode embeddingNode = item.path("embedding");
            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }
            vectors.add(vector);
        }
        return vectors;
    }
}
