package com.goodda.jejuday.crawler.config;

import com.goodda.jejuday.crawler.service.FestivalSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 배너용 축제 동기화 스케줄러.
 * 매주 월요일 새벽 4시(KST) 비짓제주에서 전량 조회 후 업서트한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final FestivalSyncService festivalSyncService;

    @Scheduled(cron = "0 0 4 * * MON", zone = "Asia/Seoul")
    @SchedulerLock(name = "festivalSync", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void syncWeekly() {
        try {
            int n = festivalSyncService.syncFestivals();
            log.info("[Scheduler] 축제 동기화 완료. upserted={}", n);
        } catch (Exception e) {
            log.error("[Scheduler] 축제 동기화 실패", e);
        }
    }
}