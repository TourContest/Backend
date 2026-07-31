package com.goodda.jejuday.spot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.entity.UserTheme;
import com.goodda.jejuday.auth.repository.UserThemeRepository;
import com.goodda.jejuday.auth.util.SecurityUtil;
import com.goodda.jejuday.openai.service.OpenAiRerankService;
import com.goodda.jejuday.openai.service.OpenAiRerankService.RerankCandidate;
import com.goodda.jejuday.spot.dto.SpotRecommendationResponse;
import com.goodda.jejuday.spot.entity.Spot;
import com.goodda.jejuday.spot.entity.SpotCongestion;
import com.goodda.jejuday.spot.entity.SpotDetail;
import com.goodda.jejuday.spot.entity.SpotEmbedding;
import com.goodda.jejuday.spot.repository.SpotCongestionRepository;
import com.goodda.jejuday.spot.repository.SpotDetailRepository;
import com.goodda.jejuday.spot.repository.SpotEmbeddingRepository;
import com.goodda.jejuday.spot.repository.SpotRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotRecommendationServiceImpl implements SpotRecommendationService {

    private static final int WALK_RADIUS_KM = 2; // 도보권. 필요 시 조정
    private static final int TOP_K_FOR_LLM = 12;
    private static final int FINAL_COUNT = 3;
    private static final double DISTANCE_WEIGHT = 0.3; // 유사도 대비 거리 페널티 가중치
    private static final double CONGESTION_WEIGHT = 0.4; // 유사도 대비 혼잡도 페널티 가중치 - 오버투어리즘 분산이 핵심이라 거리보다 비중을 더 둠
    private static final double EARTH_RADIUS_M = 6371000.0;

    private final SpotRepository spotRepository;
    private final SpotDetailRepository spotDetailRepository;
    private final SpotEmbeddingRepository spotEmbeddingRepository;
    private final SpotCongestionRepository spotCongestionRepository;
    private final UserThemeRepository userThemeRepository;
    private final SecurityUtil securityUtil;
    private final OpenAiRerankService rerankService;
    private final ObjectMapper objectMapper;

    private record ScoredCandidate(Spot spot, double distanceMeters, double score, Double congestionScore) {}

    /**
     * 트랜잭션으로 안 묶는다 - GPT rerank 호출(외부 네트워크)이 메서드 안에 있어서, 트랜잭션으로
     * 감싸면 그 호출 동안 DB 커넥션을 붙잡고 있다가 타임아웃날 수 있다. 필요한 값은 다 즉시 로딩해서
     * 쓰므로(지연 로딩 접근 없음) 트랜잭션 없이도 안전하다.
     */
    @Override
    public List<SpotRecommendationResponse> recommend(Long baseSpotId) {
        Spot base = spotRepository.findById(baseSpotId)
                .orElseThrow(() -> new EntityNotFoundException("Spot not found: " + baseSpotId));

        List<Spot> candidates = spotRepository.findWithinRadius(base.getLatitude(), base.getLongitude(), WALK_RADIUS_KM)
                .stream()
                .filter(s -> !s.getId().equals(baseSpotId))
                .filter(s -> s.getType() == Spot.SpotType.SPOT || s.getType() == Spot.SpotType.CHALLENGE)
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .toList();
        if (candidates.isEmpty()) return List.of();

        List<UserTheme> userThemes = loadUserThemes();
        List<float[]> userThemeVectors = userThemes.stream()
                .map(UserTheme::getEmbeddingJson)
                .filter(json -> json != null)
                .map(this::decode)
                .toList();
        List<String> userThemeNames = userThemes.stream().map(UserTheme::getName).toList();

        List<Long> candidateIds = candidates.stream().map(Spot::getId).toList();
        Map<Long, SpotEmbedding> embeddingsBySpotId = spotEmbeddingRepository
                .findBySpotIdIn(candidateIds)
                .stream()
                .collect(Collectors.toMap(SpotEmbedding::getSpotId, e -> e));
        Map<Long, Double> congestionBySpotId = spotCongestionRepository
                .findBySpotIdInAndCongestionDate(candidateIds, LocalDate.now())
                .stream()
                .collect(Collectors.toMap(SpotCongestion::getSpotId, SpotCongestion::getCongestionScore));

        List<ScoredCandidate> scored = candidates.stream()
                .map(spot -> score(spot, base, userThemeVectors, embeddingsBySpotId.get(spot.getId()),
                        congestionBySpotId.get(spot.getId())))
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed())
                .toList();

        List<ScoredCandidate> topK = scored.stream().limit(TOP_K_FOR_LLM).toList();

        List<Long> finalIds = rerankOrFallback(topK, userThemeNames);
        return toResponses(finalIds, topK);
    }

    private List<UserTheme> loadUserThemes() {
        try {
            User user = securityUtil.getAuthenticatedUser();
            List<Long> themeIds = userThemeRepository.findThemeIdsByUserId(user.getId());
            return themeIds.isEmpty() ? List.of() : userThemeRepository.findAllById(themeIds);
        } catch (Exception e) {
            // 비로그인 등으로 인증 정보를 못 가져오면 테마 매칭 없이 거리 기준으로만 폴백
            log.warn("유저 테마 로드 실패, 거리 기준으로만 추천: {}", e.toString());
            return List.of();
        }
    }

    private ScoredCandidate score(Spot spot, Spot base, List<float[]> userThemeVectors, SpotEmbedding embedding,
                                   Double congestionScore) {
        double distanceMeters = distanceMeters(base.getLatitude(), base.getLongitude(), spot.getLatitude(), spot.getLongitude());
        double distanceNorm = distanceMeters / (WALK_RADIUS_KM * 1000.0);

        double similarity = 0.0;
        if (!userThemeVectors.isEmpty() && embedding != null) {
            float[] spotVector = decode(embedding.getEmbeddingJson());
            similarity = userThemeVectors.stream()
                    .mapToDouble(themeVec -> cosineSimilarity(themeVec, spotVector))
                    .max().orElse(0.0);
        }

        // 혼잡도 데이터가 없는 스팟은 페널티 없음(모르는 걸 붐빈다고 단정하지 않음) - 0(한산)~1(매우혼잡)
        double congestion = (congestionScore != null) ? congestionScore : 0.0;

        double score = similarity - DISTANCE_WEIGHT * distanceNorm - CONGESTION_WEIGHT * congestion;
        return new ScoredCandidate(spot, distanceMeters, score, congestionScore);
    }

    private List<Long> rerankOrFallback(List<ScoredCandidate> topK, List<String> userThemeNames) {
        if (topK.isEmpty()) return List.of();
        try {
            List<RerankCandidate> rerankCandidates = topK.stream()
                    .map(c -> new RerankCandidate(
                            c.spot().getId(),
                            c.spot().getName(),
                            c.spot().getCategoryName(),
                            overviewSnippet(c.spot().getId()),
                            c.distanceMeters(),
                            c.congestionScore()))
                    .toList();
            List<Long> reranked = rerankService.rerankTop3(userThemeNames, rerankCandidates);
            if (!reranked.isEmpty()) return reranked;
        } catch (Exception e) {
            log.warn("GPT rerank 실패, 임베딩 스코어 순으로 폴백: {}", e.toString());
        }
        return topK.stream().limit(FINAL_COUNT).map(c -> c.spot().getId()).toList();
    }

    private List<SpotRecommendationResponse> toResponses(List<Long> orderedIds, List<ScoredCandidate> topK) {
        Map<Long, ScoredCandidate> byId = topK.stream()
                .collect(Collectors.toMap(c -> c.spot().getId(), c -> c));

        List<SpotRecommendationResponse> result = new ArrayList<>();
        for (Long id : orderedIds.stream().limit(FINAL_COUNT).toList()) {
            ScoredCandidate c = byId.get(id);
            if (c == null) continue;
            Spot spot = c.spot();
            result.add(new SpotRecommendationResponse(
                    spot.getId(),
                    spot.getName(),
                    spot.getLatitude(),
                    spot.getLongitude(),
                    c.distanceMeters(),
                    spot.getImageUrls(),
                    overviewSnippet(spot.getId()),
                    spot.getCategoryName(),
                    c.congestionScore()
            ));
        }
        return result;
    }

    private String overviewSnippet(Long spotId) {
        return spotDetailRepository.findBySpotId(spotId)
                .map(SpotDetail::getOverview)
                .filter(o -> o != null && !o.isBlank())
                .map(o -> o.substring(0, Math.min(o.length(), 200)))
                .orElse(null);
    }

    private float[] decode(String embeddingJson) {
        try {
            return objectMapper.readValue(embeddingJson, float[].class);
        } catch (Exception e) {
            throw new IllegalStateException("임베딩 디코딩 실패", e);
        }
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static double distanceMeters(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLon = Math.toRadians(lon2.doubleValue() - lon1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue())) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }
}
