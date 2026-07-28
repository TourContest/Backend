package com.goodda.jejuday.crawler.service;

import com.goodda.jejuday.crawler.entitiy.JejuEvent;
import com.goodda.jejuday.crawler.visitjeju.VisitJejuClient;
import com.goodda.jejuday.crawler.visitjeju.dto.VisitJejuItem;
import com.goodda.jejuday.crawler.visitjeju.dto.VisitJejuPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 커뮤니티 상단 배너용 제주 축제·행사 동기화.
 *
 * 비짓제주(제주관광공사) searchList 를 전량 조회한 뒤 contentscd=c5(축제/행사)만 적재한다.
 * 기존 Selenium 크롤링과 동일한 contentsid 체계를 사용하므로 데이터 연속성이 유지된다.
 *
 * 이 API는 행사 기간을 제공하지 않는다. 지난 행사는
 * (1) 제목에 박힌 연도로 1차 필터링하고,
 * (2) 운영자가 bannerVisible 플래그로 수동 제외한다.
 *
 * HTTP 조회는 트랜잭션 밖에서 수행하고 적재만 청크 단위로 트랜잭션을 연다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalSyncService {

    private static final String DETAIL_URL_PREFIX =
            "https://www.visitjeju.net/kr/detail/view?contentsid=";

    private static final int MAX_PAGES = 80;
    private static final int CHUNK = 100;

    /** 제목에 포함된 4자리 연도 */
    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");

    private final VisitJejuClient visitJejuClient;
    private final JejuEventCrawlerService crawlerService;

    public int syncFestivals() {
        int currentYear = LocalDate.now().getYear();

        List<VisitJejuItem> festivals = fetchFestivals();
        if (festivals.isEmpty()) {
            log.warn("[FestivalSync] 비짓제주 축제 수집 0건");
            return 0;
        }

        List<JejuEvent> targets = new ArrayList<>();
        int droppedByYear = 0;

        for (VisitJejuItem vj : festivals) {
            if (isStaleByTitleYear(vj.getTitle(), currentYear)) {
                droppedByYear++;
                continue;
            }
            JejuEvent e = toJejuEvent(vj);
            if (e != null) targets.add(e);
        }

        int upserted = 0;
        for (int i = 0; i < targets.size(); i += CHUNK) {
            upserted += crawlerService.upsertAll(
                    targets.subList(i, Math.min(i + CHUNK, targets.size())));
        }

        log.info("[FestivalSync] 완료: 수집 {}건, 지난연도 제외 {}건, 업서트 {}건",
                festivals.size(), droppedByYear, upserted);
        return upserted;
    }

    /** 전 페이지 조회 후 c5(축제/행사)만 추린다 */
    private List<VisitJejuItem> fetchFestivals() {
        List<VisitJejuItem> result = new ArrayList<>();
        int page = 1;

        while (page <= MAX_PAGES) {
            VisitJejuPage p;
            try {
                p = visitJejuClient.searchList(page);
            } catch (Exception ex) {
                log.error("[FestivalSync] 비짓제주 조회 실패 (page={})", page, ex);
                break;
            }
            if (p.getItems().isEmpty()) break;

            for (VisitJejuItem it : p.getItems()) {
                if (it.isFestival() && it.getContentsId() != null) result.add(it);
            }
            if (p.getPageCount() > 0 && page >= p.getPageCount()) break;
            page++;
        }
        return result;
    }

    private JejuEvent toJejuEvent(VisitJejuItem vj) {
        if (vj.getContentsId() == null || vj.getContentsId().isBlank()) return null;

        JejuEvent e = new JejuEvent();
        e.setContentsId(vj.getContentsId());
        e.setTitle(vj.getTitle());
        e.setSubTitle(vj.getIntroduction());
        e.setLocation(pickAddress(vj));
        e.setImageUrl(vj.getImgPath() != null ? vj.getImgPath() : vj.getThumbnailPath());
        e.setDetailUrl(DETAIL_URL_PREFIX + vj.getContentsId());
        e.setLatitude(vj.getLatitude());
        e.setLongitude(vj.getLongitude());
        // periodStart/periodEnd/periodText 는 비짓제주 미제공
        return e;
    }

    private String pickAddress(VisitJejuItem vj) {
        String road = vj.getRoadAddress();
        if (road != null && !road.isBlank() && !"-".equals(road.trim())) return road;
        return vj.getAddress();
    }

    /** 제목에 올해보다 이전 연도가 박혀 있으면 지난 행사로 간주 */
    private boolean isStaleByTitleYear(String title, int currentYear) {
        if (title == null) return false;
        Matcher m = YEAR.matcher(title);
        if (!m.find()) return false;
        try {
            return Integer.parseInt(m.group()) < currentYear;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}