package com.goodda.jejuday.crawler.service;

import com.goodda.jejuday.crawler.entitiy.JejuEvent;
import com.goodda.jejuday.spot.tourapi.TourApiClient;
import com.goodda.jejuday.spot.tourapi.dto.TourApiPage;
import com.goodda.jejuday.spot.tourapi.dto.TourItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 한국관광공사 TourAPI(searchFestival2)로 제주 축제·행사를 동기화한다.
 * 기존 VisitJeju Selenium 크롤링을 대체한다.
 *
 * HTTP 조회는 트랜잭션 밖에서 수행하고 적재만 청크 단위로 트랜잭션을 연다.
 * 네트워크 대기 중 DB 커넥션을 점유하지 않기 위함이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalSyncService {

    private static final String AREA_CODE_JEJU = "39";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 20;
    private static final int LOOKBACK_MONTHS = 3;
    private static final int CHUNK = 100;

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DOT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final TourApiClient tourApiClient;
    private final JejuEventCrawlerService crawlerService;

    public int syncJejuFestivals() {
        LocalDate today = LocalDate.now();
        String since = today.minusMonths(LOOKBACK_MONTHS).format(YMD);

        List<JejuEvent> collected = fetchAll(since, today);
        if (collected.isEmpty()) {
            log.warn("[FestivalSync] 수집 결과 없음 (since={})", since);
            return 0;
        }

        int upserted = 0;
        for (int i = 0; i < collected.size(); i += CHUNK) {
            List<JejuEvent> chunk = collected.subList(i, Math.min(i + CHUNK, collected.size()));
            upserted += crawlerService.upsertAll(chunk);
        }
        log.info("[FestivalSync] 완료: 수집 {}건, 업서트 {}건", collected.size(), upserted);
        return upserted;
    }

    /** 페이징으로 전량 조회 후 종료된 행사를 걸러낸다 */
    private List<JejuEvent> fetchAll(String since, LocalDate today) {
        List<JejuEvent> result = new ArrayList<>();
        int pageNo = 1;

        while (pageNo <= MAX_PAGES) {
            TourApiPage page = tourApiClient.searchFestival(since, pageNo, PAGE_SIZE, AREA_CODE_JEJU);
            if (page.getItems().isEmpty()) break;

            for (TourItem item : page.getItems()) {
                JejuEvent e = toJejuEvent(item);
                if (e == null) continue;
                if (e.getPeriodEnd() != null && e.getPeriodEnd().isBefore(today)) continue;
                result.add(e);
            }

            if (pageNo * PAGE_SIZE >= page.getTotalCount()) break;
            pageNo++;
        }
        return result;
    }

    private JejuEvent toJejuEvent(TourItem item) {
        if (item.getContentid() == null || item.getContentid().isBlank()) return null;

        JejuEvent e = new JejuEvent();
        e.setContentsId(item.getContentid());
        e.setTitle(item.getTitle());

        LocalDate start = parseYmd(item.getEventstartdate());
        LocalDate end = parseYmd(item.getEventenddate());
        e.setPeriodStart(start);
        e.setPeriodEnd(end);
        e.setPeriodText(buildPeriodText(start, end));

        e.setLocation(buildLocation(item.getAddr1(), item.getAddr2()));

        String img = blankToNull(item.getFirstimage());
        e.setImageUrl(img != null ? img : blankToNull(item.getFirstimage2()));

        // TourAPI는 웹 상세 URL을 제공하지 않는다. 앱 내부 상세 화면에서 contentsId로 조회한다.
        e.setDetailUrl(null);
        // likesCount / reviewsCount는 VisitJeju 전용 지표라 미제공
        return e;
    }

    private LocalDate parseYmd(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim(), YMD);
        } catch (Exception ex) {
            log.debug("[FestivalSync] 날짜 파싱 실패: {}", raw);
            return null;
        }
    }

    private String buildPeriodText(LocalDate start, LocalDate end) {
        if (start == null && end == null) return null;
        if (start != null && end != null) return start.format(DOT) + " ~ " + end.format(DOT);
        return (start != null ? start : end).format(DOT);
    }

    private String buildLocation(String addr1, String addr2) {
        String a = blankToNull(addr1);
        String b = blankToNull(addr2);
        if (a == null) return b;
        return (b == null) ? a : a + " " + b;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}