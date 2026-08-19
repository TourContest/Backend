package com.goodda.jejuday.spot.tourapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.goodda.jejuday.spot.entity.*;
import com.goodda.jejuday.spot.repository.*;
import com.goodda.jejuday.spot.tourapi.TourDataClient;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 집중률·지역 방문자·연관 관광지를 로컬 추천 데이터로 변환하는 배치. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourAnalyticsSyncService {
    private static final int ROWS = 1000;
    private static final int MAX_PAGES = 30;
    private static final String[] LEGACY_AREA_CODES =
            {"1","2","3","4","5","6","7","8","31","32","33","34","35","36","37","38","39"};

    private final TourDataClient client;
    private final SpotRepository spotRepository;
    private final SpotCongestionRepository congestionRepository;
    private final SpotRelationRepository relationRepository;
    private final RegionalVisitorRepository visitorRepository;

    @Scheduled(cron = "0 0 4 * * *")
    @SchedulerLock(name = "tourAnalyticsDaily", lockAtMostFor = "PT40M", lockAtLeastFor = "PT1M")
    public void syncDaily() {
        SyncResult result = new SyncResult(syncCongestion(), syncVisitors());
        log.info("관광 분석 일일 동기화 완료: {}", result);
    }

    @Scheduled(cron = "0 0 5 * * MON")
    @SchedulerLock(name = "tourRelationsWeekly", lockAtMostFor = "PT60M", lockAtLeastFor = "PT1M")
    public void syncRelationsWeekly() {
        log.info("연관 관광지 동기화 완료: {}건", syncRelations());
    }

    @Transactional
    public int syncCongestion() {
        int saved = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            List<JsonNode> rows = client.congestionForecast(page, ROWS);
            for (JsonNode row : rows) saved += saveCongestion(row);
            if (rows.size() < ROWS) break;
        }
        return saved;
    }

    private int saveCongestion(JsonNode row) {
        String name = text(row, "tAtsNm", "tatsNm", "title");
        LocalDate date = date(text(row, "baseYmd", "baseDate"));
        Double rate = number(row, "cnctrRate", "congestionRate");
        if (name == null || date == null || rate == null) return 0;
        Optional<Spot> spot = spotRepository.findFirstByNameIgnoreCaseAndUserCreatedFalse(name.trim());
        if (spot.isEmpty()) return 0;
        SpotCongestion value = congestionRepository.findBySpotIdAndCongestionDate(spot.get().getId(), date)
                .orElseGet(SpotCongestion::new);
        value.setSpotId(spot.get().getId());
        value.setCongestionDate(date);
        value.setExternalScore(clamp(rate / 100.0));
        value.setCongestionScore(blend(value.getExternalScore(), value.getInternalScore()));
        value.setSource(value.getInternalScore() == null ? "kto_forecast" : "kto_forecast+internal");
        value.setUpdatedAt(LocalDateTime.now());
        congestionRepository.save(value);
        return 1;
    }

    @Transactional
    public int syncVisitors() {
        LocalDate target = LocalDate.now().minusDays(1);
        List<JsonNode> all = new ArrayList<>();
        all.addAll(client.regionalVisitors(false, target, target, 1, ROWS));
        all.addAll(client.regionalVisitors(true, target, target, 1, ROWS));
        long max = all.stream().mapToLong(r -> Optional.ofNullable(longNumber(r, "visitrCnt", "visitorCnt", "touNum")).orElse(0L)).max().orElse(0L);
        int saved = 0;
        for (JsonNode row : all) {
            String code = text(row, "signguCd", "locgoCd", "areaCd", "metcoCd");
            String name = text(row, "signguNm", "locgoNm", "areaNm", "metcoNm");
            Long count = longNumber(row, "visitrCnt", "visitorCnt", "touNum");
            LocalDate date = Optional.ofNullable(date(text(row, "baseYmd", "baseDate"))).orElse(target);
            if (code == null || count == null) continue;
            String level = row.has("signguCd") || row.has("locgoCd") ? "LOCAL" : "METRO";
            RegionalVisitor value = visitorRepository.findByBaseDateAndRegionCodeAndRegionLevel(date, code, level)
                    .orElseGet(RegionalVisitor::new);
            value.setBaseDate(date); value.setRegionCode(code); value.setRegionName(name); value.setRegionLevel(level);
            value.setVisitorCount(count); value.setNormalizedScore(max == 0 ? 0 : count / (double) max);
            value.setUpdatedAt(LocalDateTime.now()); visitorRepository.save(value); saved++;
        }
        return saved;
    }

    @Transactional
    public int syncRelations() {
        int saved = 0;
        for (String area : LEGACY_AREA_CODES) {
            for (int page = 1; page <= MAX_PAGES; page++) {
                List<JsonNode> rows = client.relatedTour(area, null, page, ROWS);
                for (JsonNode row : rows) saved += saveRelation(row);
                if (rows.size() < ROWS) break;
            }
        }
        return saved;
    }

    private int saveRelation(JsonNode row) {
        String sourceName = text(row, "tAtsNm", "tatsNm", "baseTatsNm", "srcTatsNm");
        String targetName = text(row, "rlteTatsNm", "relatedTatsNm", "tarTatsNm");
        if (sourceName == null || targetName == null) return 0;
        Optional<Spot> source = spotRepository.findFirstByNameIgnoreCaseAndUserCreatedFalse(sourceName.trim());
        Optional<Spot> target = spotRepository.findFirstByNameIgnoreCaseAndUserCreatedFalse(targetName.trim());
        if (source.isEmpty() || target.isEmpty()) return 0;
        String type = Optional.ofNullable(text(row, "rlteCtgryNm", "relationType", "typeNm")).orElse("ALL");
        SpotRelation value = relationRepository.findBySourceSpotIdAndTargetSpotIdAndRelationType(
                source.get().getId(), target.get().getId(), type).orElseGet(SpotRelation::new);
        value.setSourceSpotId(source.get().getId()); value.setTargetSpotId(target.get().getId()); value.setRelationType(type);
        Double rank = number(row, "rlteRank", "rank", "rnum");
        value.setRelationRank(rank == null ? null : rank.intValue());
        value.setRelationScore(rank == null ? null : 1.0 / Math.max(1.0, rank));
        value.setSourcePeriod(text(row, "baseYm")); value.setUpdatedAt(LocalDateTime.now());
        relationRepository.save(value); return 1;
    }

    private static String text(JsonNode row, String... keys) { for (String k : keys) { String v=row.path(k).asText(null); if(v!=null&&!v.isBlank()) return v; } return null; }
    private static Double number(JsonNode row, String... keys) { String v=text(row, keys); try{return v==null?null:Double.valueOf(v.replace(",",""));}catch(Exception e){return null;} }
    private static Long longNumber(JsonNode row, String... keys) { Double v=number(row, keys); return v==null?null:v.longValue(); }
    private static LocalDate date(String value) { try{return value==null?null:LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);}catch(Exception e){return null;} }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    private static double blend(Double external, Double internal) { if(external==null)return internal==null?0:internal; if(internal==null)return external; return .7*external+.3*internal; }
    public record SyncResult(int congestion, int visitors) {}
}
