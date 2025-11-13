package com.goodda.jejuday.crawler.config;

import com.goodda.jejuday.crawler.service.JejuEventCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collection;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final JejuEventCrawlerService crawlerService;

    /** 매일 새벽 4시(한국 시간) 현재월 1회 크롤링 */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void crawlNightly() {
        try {
            Collection<?> saved = crawlerService.crawlSingleMonth(null);
            log.info("[Scheduler] Nightly crawl done. upserted={}", saved.size());
        } catch (Exception e) {
            log.error("[Scheduler] Nightly crawl failed", e);
        }
    }
}