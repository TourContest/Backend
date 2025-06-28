package com.goodda.jejuday.Notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class PopularityScheduler {

    private final RedisTemplate<String, String> redisTemplate;

    @Scheduled(cron = "0 0 * * * *")
    public void cleanUpOldPopularPosts() {
        Set<String> keys = redisTemplate.opsForZSet().range("community:ranking", 0, -1);
    }
}
