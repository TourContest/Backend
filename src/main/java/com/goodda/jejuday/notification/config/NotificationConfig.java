package com.goodda.jejuday.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

@Data
@Configuration
@EnableAsync
@EnableScheduling
@ConfigurationProperties(prefix = "notification")
public class NotificationConfig {

    private Cache cache = new Cache();
    private Promotion promotion = new Promotion();
    private Scoring scoring = new Scoring();
    private Like like = new Like();
    private Fcm fcm = new Fcm();

    @Data
    public static class Cache {
        private Duration scoreTtl = Duration.ofMinutes(30);
        private Duration statsTtl = Duration.ofMinutes(10);
        private Duration duplicatePreventionTtl = Duration.ofSeconds(5);
    }

    @Data
    public static class Promotion {
        private int postToSpotThreshold = 10;
        private double spotToChallengePercentage = 0.3;
        private int timeDecayDays = 14;
        private double minTimeWeight = 0.1;
    }

    @Data
    public static class Scoring {
        private int replyWeight = 1;
        private int likeWeight = 3;
        private int viewWeight = 2;
    }

    @Data
    public static class Like {
        private int milestoneInterval = 50;
    }

    @Data
    public static class Fcm {
        private boolean enabled = true;
        private String defaultTitle = "[제주데이]";
        private int connectionTimeout = 5000;
        private int readTimeout = 10000;
    }
}