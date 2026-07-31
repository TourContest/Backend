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

/**
 * SPOT(공공데이터) 상위 3곳 + CHALLENGE(UGC 승격) 상위 1곳, 총 최대 4곳을 추천한다.
 * 두 타입을 별도 후보 풀로 스코어링한다 - 섞어서 상위 4개를 뽑으면 SPOT이 더 많아서(수가 압도적으로 많음)
 * CHALLENGE가 아예 안 뽑힐 수 있기 때문. CHALLENGE는 개수가 적어 SPOT보다 넓은 반경에서 찾는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotRecommendationServiceImpl implements SpotRecommendationService {

    private static final int SPOT_RADIUS_KM = 2; // 도보권
    private static final int CHALLENGE_RADIUS_KM = 10; // 챌린지는 희소해서 더 넓게 탐색
    private static final int TOP_K_FOR_LLM = 12;
    private static final int SPOT_FINAL_COUNT = 3;
    private static final int CHALLENGE_FINAL_COUNT = 1;
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

        List<UserTheme> userThemes = loadUserThemes();
        List<float[]> userThemeVectors = userThemes.stream()
                .map(UserTheme::getEmbeddingJson)
                .filter(json -> json != null)
                .map(this::decode)
                .toList();
        List<String> userThemeNames = userThemes.stream().map(UserTheme::getName).toList();

        List<ScoredCandidate> spotScored = scoreCandidates(base, Spot.SpotType.SPOT, SPOT_RADIUS_KM, userThemeVectors);
        List<ScoredCandidate> challengeScored = scoreCandidates(base, Spot.SpotType.CHALLENGE, CHALLENGE_RADIUS_KM, userThemeVectors);

        List<SpotRecommendationResponse> result = new ArrayList<>();
        result.addAll(recommendSpots(spotScored, userThemeNames));
        result.addAll(recommendTopChallenge(challengeScored));
        return result;
    }

    private List<ScoredCandidate> scoreCandidates(Spot base, Spot.SpotType type, int radiusKm, List<float[]> userThemeVectors) {
        List<Spot> candidates = spotRepository.findWithinRadius(base.getLatitude(), base.getLongitude(), radiusKm)
                .stream()
                .filter(s -> !s.getId().equals(base.getId()))
                .filter(s -> s.getType() == type)
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .toList();
        if (candidates.isEmpty()) return List.of();

        List<Long> candidateIds = candidates.stream().map(Spot::getId).toList();
        Map<Long, SpotEmbedding> embeddingsBySpotId = spotEmbeddingRepository
                .findBySpotIdIn(candidateIds)
                .stream()
                .collect(Collectors.toMap(SpotEmbedding::getSpotId, e -> e));
        Map<Long, Double> congestionBySpotId = spotCongestionRepository
                .findBySpotIdInAndCongestionDate(candidateIds, LocalDate.now())
                .stream()
                .collect(Collectors.toMap(SpotCongestion::getSpotId, SpotCongestion::getCongestionScore));

        return candidates.stream()
                .map(spot -> score(spot, base, radiusKm, userThemeVectors, embeddingsBySpotId.get(spot.getId()),
                        congestionBySpotId.get(spot.getId())))
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed())
                .toList();
    }

    private List<SpotRecommendationResponse> recommendSpots(List<ScoredCandidate> scored, List<String> userThemeNames) {
        if (scored.isEmpty()) return List.of();
        List<ScoredCandidate> topK = scored.stream().limit(TOP_K_FOR_LLM).toList();
        List<Long> finalIds = rerankOrFallback(topK, userThemeNames, SPOT_FINAL_COUNT);
        return toResponses(finalIds, topK, "SPOT");
    }

    private List<SpotRecommendationResponse> recommendTopChallenge(List<ScoredCandidate> scored) {
        if (scored.isEmpty()) return List.of();
        // 최종 1곳만 뽑으므로 GPT rerank는 생략하고 임베딩/거리/혼잡도 점수 1위를 그대로 채택한다.
        List<ScoredCandidate> top = scored.stream().limit(CHALLENGE_FINAL_COUNT).toList();
        List<Long> ids = top.stream().map(c -> c.spot().getId()).toList();
        return toResponses(ids, top, "CHALLENGE");
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

    private ScoredCandidate score(Spot spot, Spot base, int radiusKm, List<float[]> userThemeVectors, SpotEmbedding embedding,
                                   Double congestionScore) {
        double distanceMeters = distanceMeters(base.getLatitude(), base.getLongitude(), spot.getLatitude(), spot.getLongitude());
        double distanceNorm = distanceMeters / (radiusKm * 1000.0);

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

    private List<Long> rerankOrFallback(List<ScoredCandidate> topK, List<String> userThemeNames, int finalCount) {
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
        return topK.stream().limit(finalCount).map(c -> c.spot().getId()).toList();
    }

    private List<SpotRecommendationResponse> toResponses(List<Long> orderedIds, List<ScoredCandidate> pool, String type) {
        Map<Long, ScoredCandidate> byId = pool.stream()
                .collect(Collectors.toMap(c -> c.spot().getId(), c -> c));

        List<SpotRecommendationResponse> result = new ArrayList<>();
        for (Long id : orderedIds) {
            ScoredCandidate c = byId.get(id);
            if (c == null) continue;
            Spot spot = c.spot();
            result.add(new SpotRecommendationResponse(
                    spot.getId(),
                    spot.getName(),
                    type,
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
