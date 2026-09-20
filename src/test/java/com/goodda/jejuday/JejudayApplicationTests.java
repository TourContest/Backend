package com.goodda.jejuday;

import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 애플리케이션 컨텍스트가 뜨는지만 확인한다.
 * 프로필 없이 application.yml 을 그대로 읽으면 ${AWS_ACCESS_KEY_ID} 같은 환경변수 플레이스홀더가 풀리지 않아 실패했다.
 * 다른 통합 테스트와 같이 it 프로필(src/test/resources/application-it.yml: Testcontainers MySQL, 더미 시크릿)과
 * Firebase/Redis 목 빈을 사용한다.
 */
@SpringBootTest
@ActiveProfiles("it")
class JejudayApplicationTests {

    @MockitoBean private FirebaseMessaging firebaseMessaging;
    @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private RedisTemplate<String, String> redisTemplate;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
    }

}
