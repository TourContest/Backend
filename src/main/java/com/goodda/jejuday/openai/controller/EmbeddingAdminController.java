package com.goodda.jejuday.openai.controller;

import com.goodda.jejuday.openai.service.SpotEmbeddingBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Embedding Admin", description = "스팟 임베딩(OpenAI) 보강 배치 관리자 API")
@RestController
@RequestMapping("/api/admin/embeddings")
@RequiredArgsConstructor
@Validated
public class EmbeddingAdminController {

    private final SpotEmbeddingBatchService batchService;

    @Operation(summary = "스팟 임베딩 보강 배치", description = "임베딩이 없거나 오래된 SPOT/CHALLENGE 타입 스팟을 대상으로 OpenAI 임베딩을 생성/갱신합니다. TourAPI detail-sync를 먼저 실행해 overview를 채운 뒤 호출하는 것이 품질상 유리합니다.")
    @PostMapping("/spots/sync")
    public Map<String, Object> syncSpotEmbeddings(@RequestParam(defaultValue = "200") @Min(1) @Max(1000) int limit) {
        var r = batchService.embedMissingOrStale(limit);
        return Map.of("processed", r.processed(), "updated", r.updated(), "failed", r.failed());
    }
}
