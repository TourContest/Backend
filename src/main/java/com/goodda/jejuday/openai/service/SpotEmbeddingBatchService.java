package com.goodda.jejuday.openai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodda.jejuday.openai.OpenAiProperties;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.entity.SpotDetail;
import com.goodda.jejuday.spot.entity.SpotEmbedding;
import com.goodda.jejuday.spot.repository.SpotDetailRepository;
import com.goodda.jejuday.spot.repository.SpotEmbeddingRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** name+categoryName+overview(있으면)를 조합해 Spot 임베딩을 생성/저장한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotEmbeddingBatchService {

    private static final int OVERVIEW_MAX_CHARS = 500;

    private final SpotRepository spotRepository;
    private final SpotDetailRepository spotDetailRepository;
    private final SpotEmbeddingRepository spotEmbeddingRepository;
    private final OpenAiEmbeddingService embeddingService;
    private final OpenAiProperties props;
    private final ObjectMapper objectMapper;

    /**
     * 하나의 트랜잭션으로 묶지 않는다 - 묶으면 항목 하나가 JPA 예외를 내는 순간 세션 전체가
     * rollback-only로 마킹되어, 개별 try/catch로 넘어가도 마지막에 UnexpectedRollbackException이
     * 터진다. repository 메서드 호출 각각이 자체 트랜잭션을 갖게 두어 항목별 실패를 진짜로 격리한다.
     */
    public Result embedMissingOrStale(int limit) {
        List<Spot> targets = spotRepository.findMissingEmbeddingSpots(PageRequest.of(0, limit));
        if (targets.isEmpty()) return new Result(0, 0, 0);

        List<String> texts = targets.stream().map(this::buildEmbeddingText).toList();

        List<float[]> vectors;
        try {
            vectors = embeddingService.embedBatch(texts);
        } catch (Exception e) {
            log.warn("스팟 임베딩 배치 호출 실패: {}", e.toString());
            return new Result(targets.size(), 0, targets.size());
        }

        int updated = 0, failed = 0;
        for (int i = 0; i < targets.size(); i++) {
            try {
                save(targets.get(i), texts.get(i), vectors.get(i));
                updated++;
            } catch (Exception e) {
                failed++;
                log.warn("스팟 임베딩 저장 실패 spotId={}: {}", targets.get(i).getId(), e.toString());
            }
        }
        return new Result(targets.size(), updated, failed);
    }

    private void save(Spot spot, String text, float[] vector) throws Exception {
        SpotEmbedding embedding = spotEmbeddingRepository.findBySpotId(spot.getId()).orElseGet(SpotEmbedding::new);
        embedding.setSpotId(spot.getId());
        embedding.setEmbeddingJson(objectMapper.writeValueAsString(vector));
        embedding.setModel(props.getEmbeddingModel());
        embedding.setSourceHash(sha256(text));
        embedding.setUpdatedAt(LocalDateTime.now());
        spotEmbeddingRepository.save(embedding);
    }

    private String buildEmbeddingText(Spot spot) {
        StringBuilder sb = new StringBuilder();
        if (spot.getName() != null) sb.append(spot.getName()).append(" ");
        if (spot.getCategoryName() != null) sb.append(spot.getCategoryName()).append(" ");

        spotDetailRepository.findBySpotId(spot.getId()).map(SpotDetail::getOverview).ifPresent(overview -> {
            if (overview != null && !overview.isBlank()) {
                sb.append(overview.substring(0, Math.min(overview.length(), OVERVIEW_MAX_CHARS)));
            }
        });
        return sb.toString().trim();
    }

    private String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    public record Result(int processed, int updated, int failed) {}
}
