package com.goodda.jejuday.crawler.service;

import com.goodda.jejuday.crawler.entitiy.JejuEvent;
import com.goodda.jejuday.crawler.repository.JejuEventRepository;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JejuEventCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(JejuEventCrawlerService.class);

    private final JejuEventRepository repository;
    private final DateTimeFormatter periodFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(12);

    private static final Pattern PERIOD_RANGE =
            Pattern.compile("(\\d{4}\\.\\d{2}\\.\\d{2})\\s*~\\s*(\\d{4}\\.\\d{2}\\.\\d{2})");
    private static final Pattern TWO_NUMBERS = Pattern.compile("(\\d+)");
    private static final Pattern CONTENTSID = Pattern.compile("contentsid=([^&#]+)");

    public JejuEventCrawlerService(JejuEventRepository repository) {
        this.repository = repository;
    }

    /** 외부(li.outerHTML 배열) 저장용은 유지 */
    @Transactional
    public List<JejuEvent> saveFromRawLiHtml(List<String> rawLis) {
        List<JejuEvent> saved = new ArrayList<>();
        for (String liHtml : rawLis) {
            try {
                JejuEvent parsed = parseOneLi(liHtml);
                if (parsed == null || parsed.getContentsId() == null) {
                    log.warn("[JejuEvent] parse skip: {}", liHtml.length() > 120 ? liHtml.substring(0, 120) + "..." : liHtml);
                    continue;
                }
                if (!isOngoingOrUpcoming(parsed, LocalDate.now())) {
                    continue;
                }
                JejuEvent upserted = upsertByContentsId(parsed);
                saved.add(upserted);
            } catch (Exception e) {
                log.error("[JejuEvent] parse error: {}", e.getMessage(), e);
            }
        }
        log.info("[JejuEvent] saveFromRawLiHtml upsert count={}", saved.size());
        return saved;
    }

    /** 지정 월(없으면 현재월)의 '한 페이지만' 크롤링하고 진행/예정만 저장 */
    @Transactional
    public List<JejuEvent> crawlSingleMonth(String monthNullable) throws Exception {
        List<JejuEvent> results = new ArrayList<>();

        // 월 파라미터 정규화 (MM)
        String monthParam;
        if (monthNullable == null || monthNullable.isBlank()) {
            monthParam = String.format("%02d", LocalDate.now().getMonthValue());
        } else {
            int m = Integer.parseInt(monthNullable);
            if (m < 1 || m > 12) throw new IllegalArgumentException("month must be 1..12");
            monthParam = String.format("%02d", m);
        }

        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);

        WebDriver driver = new ChromeDriver(options);
        try {
            // 한 페이지만 접근 (page 파라미터는 1 고정 또는 생략)
            String url = "https://www.visitjeju.net/kr/festival/list?month=" + monthParam + "&page=1";
            log.info("[JejuEvent] GET (single page, month={}) {}", monthParam, url);
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, WAIT_TIMEOUT);
            // li가 실제로 붙을 때까지 대기
            wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(
                    By.cssSelector("ul.event_list li.list_item")));

            List<WebElement> lis = driver.findElements(By.cssSelector("ul.event_list li.list_item"));
            if (lis == null || lis.isEmpty()) {
                log.info("[JejuEvent] no items for month={}", monthParam);
                return results;
            }

            LocalDate today = LocalDate.now();
            int upserted = 0;
            for (WebElement li : lis) {
                String outer = li.getAttribute("outerHTML");
                JejuEvent parsed = parseOneLi(outer);
                if (parsed == null || parsed.getContentsId() == null) continue;

                // 진행/예정 필터
                if (!isOngoingOrUpcoming(parsed, today)) continue;

                JejuEvent saved = upsertByContentsId(parsed);
                results.add(saved);
                upserted++;
            }
            log.info("[JejuEvent] month={} upserted={}", monthParam, upserted);

        } finally {
            try { driver.quit(); } catch (Exception ignore) {}
        }

        return results;
    }

    /** li.outerHTML → JejuEvent 파싱(공용) */
    private JejuEvent parseOneLi(String liHtml) {
        Document liDoc = Jsoup.parse(liHtml, "https://www.visitjeju.net");
        Element li = Optional.ofNullable(liDoc.selectFirst("li.list_item"))
                .orElse(liDoc.selectFirst("li"));

        if (li == null) return null;

        Element a = li.selectFirst("a");
        if (a == null) return null;

        String href = a.attr("href");
        String contentsId = extractContentsId(href);

        JejuEvent e = new JejuEvent();
        e.setContentsId(contentsId);
        e.setDetailUrl(href.startsWith("http") ? href : "https://www.visitjeju.net" + href);

        Element imgEl = li.selectFirst(".item_img img");
        if (imgEl != null) {
            String src = imgEl.hasAttr("src") ? imgEl.absUrl("src") : imgEl.absUrl("data-src");
            e.setImageUrl(src);
        }

        Element titleEl = li.selectFirst(".item_tit");
        Element subEl   = li.selectFirst(".item_sub_tit");
        e.setTitle(titleEl != null ? titleEl.text() : null);
        e.setSubTitle(subEl != null ? subEl.text() : null);

        Element periodEl = li.selectFirst(".item_period");
        if (periodEl != null) {
            String txt = periodEl.text().trim();
            e.setPeriodText(txt);
            Matcher m = PERIOD_RANGE.matcher(txt);
            if (m.find()) {
                e.setPeriodStart(LocalDate.parse(m.group(1), periodFmt));
                e.setPeriodEnd(LocalDate.parse(m.group(2), periodFmt));
            }
        }

        Element locEl = li.selectFirst(".item_location");
        e.setLocation(locEl != null ? locEl.text() : null);

        Element cntEl = li.selectFirst(".item_count");
        if (cntEl != null) {
            Matcher m = TWO_NUMBERS.matcher(cntEl.text());
            Integer like = null, review = null;
            if (m.find()) like = Integer.valueOf(m.group(1));
            if (m.find()) review = Integer.valueOf(m.group(1));
            e.setLikesCount(like);
            e.setReviewsCount(review);
        }
        return e;
    }

    /** 진행 중/예정 필터 (오늘 기준) */
    private boolean isOngoingOrUpcoming(JejuEvent e, LocalDate today) {
        if (e.getPeriodEnd() != null) {
            // 종료일이 오늘 이전이면 제외
            return !e.getPeriodEnd().isBefore(today);
        }
        // 날짜 파싱 실패 시 텍스트 휴리스틱
        String t = Optional.ofNullable(e.getPeriodText()).orElse("");
        if (t.contains("종료")) return false;
        if (t.contains("예정")) return true;
        // 그 외는 보수적으로 제외 (원하시면 true로 바꿔도 됩니다)
        return false;
    }

    private String extractContentsId(String href) {
        if (href == null) return null;
        Matcher m = CONTENTSID.matcher(href);
        if (m.find()) {
            return URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
        }
        return null;
    }

    private JejuEvent upsertByContentsId(JejuEvent incoming) {
        return repository.findByContentsId(incoming.getContentsId())
                .map(db -> {
                    db.setTitle(incoming.getTitle());
                    db.setSubTitle(incoming.getSubTitle());
                    db.setPeriodStart(incoming.getPeriodStart());
                    db.setPeriodEnd(incoming.getPeriodEnd());
                    db.setPeriodText(incoming.getPeriodText());
                    db.setLocation(incoming.getLocation());
                    db.setLikesCount(incoming.getLikesCount());
                    db.setReviewsCount(incoming.getReviewsCount());
                    db.setImageUrl(incoming.getImageUrl());
                    db.setDetailUrl(incoming.getDetailUrl());
                    return db; // JPA dirty checking
                })
                .orElseGet(() -> repository.save(incoming));
    }
}