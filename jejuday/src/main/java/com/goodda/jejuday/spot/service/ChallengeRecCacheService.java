package com.goodda.jejuday.spot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
public class ChallengeRecCacheService {

    private static final String KEY_PREFIX = "rec:upcoming:";
    private static final Duration TTL = Duration.ofDays(2);
    // Redis 미설치/미연결이어도 주입되게 강제하지 않음
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.enabled:false}")
    private boolean redisEnabled;



    public ChallengeRecCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void save(Long userId, List<Long> spotIds) {
        if (!redisEnabled || redis == null) return;
        try {
            String key = KEY_PREFIX + userId;
            String value = objectMapper.writeValueAsString(spotIds);
            redis.opsForValue().set(key, value, TTL);
        } catch (Exception ignore) { /* 캐시 실패 무시 */ }
    }

    public List<Long> load(Long userId) {
        if (!redisEnabled || redis == null) return null;
        try {
            String key = KEY_PREFIX + userId;
            String json = redis.opsForValue().get(key);
            if (json == null) return null;
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Long>>(){});
        } catch (Exception e) {
            return null; // 실패 시 캐시 미사용
        }
    }

    public void invalidate(Long userId) {
        if (!redisEnabled || redis == null) return;
        try {
            redis.delete(KEY_PREFIX + userId);
        } catch (Exception ignore) { /* 무시 */ }
    }
}