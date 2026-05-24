//package com.goodda.jejuday.crawler.config;
//
//import com.goodda.jejuday.crawler.service.JejuEventCrawlerService;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.scheduling.annotation.Scheduled;
//
//
//@Configuration
//@EnableScheduling
//public class SchedulerConfig {
//    private final JejuEventCrawlerService crawlerService;
//
//    public SchedulerConfig(JejuEventCrawlerService crawlerService) {
//        this.crawlerService = crawlerService;
//    }
//
//    /**
//     * 매일 새벽 4시에 크롤러 실행
//     */
//    @Scheduled(cron = "0 0 4 * * *")
//    public void runCrawler() {
//        try {
//            crawlerService.crawlNewEvents();
//        } catch (Exception e) {
//            e.printStackTrace(); // 실제 운영에서는 로거 사용
//        }
//    }
//
//    @Scheduled(cron = "0 49 23 * * *")
//    public void runCrawlerAt1125PM() {
//        try {
//            crawlerService.crawlNewEvents();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//}