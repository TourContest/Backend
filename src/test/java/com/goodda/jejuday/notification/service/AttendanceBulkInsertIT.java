package com.goodda.jejuday.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.goodda.jejuday.attendance.repository.ReminderTarget;
import com.goodda.jejuday.attendance.repository.UserAttendanceRepository;
import com.goodda.jejuday.notification.entity.NotificationType;
import com.google.firebase.messaging.FirebaseMessaging;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.QueryCountHolder;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 출석 리마인더 벌크 INSERT 통합 테스트.
 * 실제 MySQL 8.0(Testcontainers)으로 다음 두 명제를 증명한다.
 *
 * <ul>
 *   <li>IT-출석-1: N=20000명 발송 시 INSERT 쿼리 수 == ceil(N/500) = 40 (단건 N회 아님)</li>
 *   <li>IT-출석-2: 단건 방식 vs 벌크 방식 처리시간 로그 비교 (flaky assert 없음)</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("it")
class AttendanceBulkInsertIT {

    private static final Logger log = LoggerFactory.getLogger(AttendanceBulkInsertIT.class);
    private static final int N = 20_000;

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    // --- Mock 빈 ---
    @MockitoBean
    FirebaseMessaging firebaseMessaging;  // FirebaseConfig 초기화 방지

    @MockitoBean
    FcmGateway fcmGateway;

    @SuppressWarnings("rawtypes")
    @MockitoBean
    RedisTemplate redisTemplate;          // Redis 실연결 불필요

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;  // ChallengeRecCacheService 의존 만족

    // --- 실제 빈 ---
    @Autowired
    NotificationService notificationService;

    @Autowired
    UserAttendanceRepository attendanceRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    // -----------------------------------------------------------------------
    // datasource-proxy: JDBC 레벨 INSERT 쿼리 수 카운팅
    // -----------------------------------------------------------------------

    /**
     * DataSource를 datasource-proxy로 감싸 QueryCountHolder에 쿼리 카운트를 기록한다.
     * static @Bean 선언으로 BPP가 다른 빈보다 먼저 초기화되어 모든 JDBC 호출이 프록시를 통과한다.
     */
    @TestConfiguration
    static class ProxyDataSourceConfig {
        @Bean
        static BeanPostProcessor dsProxyWrapper() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName)
                        throws BeansException {
                    if (bean instanceof DataSource && !(bean instanceof ProxyDataSource)) {
                        return ProxyDataSourceBuilder
                                .create((DataSource) bean)
                                .name("jejuday-test-ds")
                                .countQuery()
                                .build();
                    }
                    return bean;
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute((Connection conn) -> {
            try (Statement s = conn.createStatement()) {
                s.execute("SET foreign_key_checks = 0");
                s.execute("DELETE FROM notification_outbox");
                s.execute("DELETE FROM users WHERE email LIKE 'bulkseed\\_%@it.test'");
                s.execute("SET foreign_key_checks = 1");
            }
            return null;
        });
        QueryCountHolder.clear();
    }

    // -----------------------------------------------------------------------
    // IT-출석-1. 쓰기 쿼리 수 일괄화 검증 (메인 지표)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("IT-출석-1: N=20000명 → INSERT 쿼리 수 == 40 (단건 20000회 아님)")
    void IT출석1_쓰기쿼리_일괄화() {
        // 1. 미출석 + 유효토큰 사용자 N명 시드
        seedUsers(N);

        // 2. 타겟 조회 — SELECT은 카운터 초기화 전에 수행 (측정에서 제외)
        List<ReminderTarget> targets = attendanceRepository.findAttendanceReminderTargets();
        assertThat(targets).as("시드된 사용자 수").hasSize(N);

        Set<Long> attendedIds = Set.of(); // 전원 미출석

        // 3. 쿼리 카운터 초기화 (시드 INSERT·SELECT 제외)
        QueryCountHolder.clear();

        // 4. 출석 리마인더 벌크 INSERT 실행
        long startNs = System.nanoTime();
        int inserted = notificationService.scheduleAttendanceReminders(targets, attendedIds, LocalDate.now());
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        // 5. INSERT 쿼리 수 검증 (핵심 단언)
        int insertQueryCount = (int) QueryCountHolder.getGrandTotal().getInsert();
        int expectedChunks = (int) Math.ceil((double) N / OutboxBulkInserter.CHUNK_SIZE);

        assertThat(insertQueryCount)
                .as("실제 실행된 INSERT 쿼리 수 (벌크: %d건 → %d청크)", N, expectedChunks)
                .isEqualTo(expectedChunks); // 40

        assertThat(inserted).as("outbox 삽입 건수").isEqualTo(N);

        log.info("[IT-출석-1] N={}, INSERT 쿼리 수={} (expected={}), 삽입={}건, 처리시간={}ms",
                N, insertQueryCount, expectedChunks, inserted, elapsedMs);
    }

    // -----------------------------------------------------------------------
    // IT-출석-2. 처리시간 before/after 비교 (보조 지표, 시간 assert 없음)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("IT-출석-2: 단건 방식 vs 벌크 방식 처리시간 로그 비교 (assert 없음)")
    void IT출석2_처리시간_벌크vs단건비교() {
        seedUsers(N);
        List<ReminderTarget> targets = attendanceRepository.findAttendanceReminderTargets();
        assertThat(targets).hasSize(N);
        Set<Long> attendedIds = Set.of();

        // === Before: 단건 방식 워밍업 1회 (버림) ===
        truncateOutbox();
        singleInsertAll(targets, attendedIds, LocalDate.of(2024, 1, 1));

        // === Before: 단건 방식 본 측정 3회, 중앙값 사용 ===
        long[] beforeNs = new long[3];
        for (int i = 0; i < 3; i++) {
            truncateOutbox();
            long t = System.nanoTime();
            singleInsertAll(targets, attendedIds, LocalDate.of(2024, 1, 2 + i));
            beforeNs[i] = System.nanoTime() - t;
        }
        long medianBeforeMs = TimeUnit.NANOSECONDS.toMillis(median(beforeNs));

        // === After: 벌크 방식 워밍업 1회 (버림) ===
        truncateOutbox();
        notificationService.scheduleAttendanceReminders(targets, attendedIds, LocalDate.of(2024, 2, 1));

        // === After: 벌크 방식 본 측정 3회, 중앙값 사용 ===
        long[] afterNs = new long[3];
        for (int i = 0; i < 3; i++) {
            truncateOutbox();
            long t = System.nanoTime();
            notificationService.scheduleAttendanceReminders(targets, attendedIds, LocalDate.of(2024, 2, 2 + i));
            afterNs[i] = System.nanoTime() - t;
        }
        long medianAfterMs = TimeUnit.NANOSECONDS.toMillis(median(afterNs));

        log.info("========================================");
        log.info("[IT-출석-2] 처리시간 비교 (N={})", N);
        log.info("  단건 방식(before): {}ms", medianBeforeMs);
        log.info("  벌크 방식(after):  {}ms", medianAfterMs);
        log.info("========================================");
        // 시간 기반 단언 없음 (환경 의존 → flaky)

        // 보조 안정성 검증: 벌크 방식 INSERT 쿼리 수 == 40
        truncateOutbox();
        QueryCountHolder.clear();
        notificationService.scheduleAttendanceReminders(targets, attendedIds, LocalDate.of(2024, 3, 1));
        assertThat((int) QueryCountHolder.getGrandTotal().getInsert())
                .as("벌크 방식 INSERT 쿼리 수")
                .isEqualTo((int) Math.ceil((double) N / OutboxBulkInserter.CHUNK_SIZE));
    }

    // -----------------------------------------------------------------------
    // 헬퍼
    // -----------------------------------------------------------------------

    /**
     * users 테이블에 N명의 시드 사용자 삽입.
     * is_notification_enabled=true, length(fcm_token) > 20 → 리마인더 대상 조건 충족.
     */
    private void seedUsers(int n) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO users " +
                "(is_kakao_login, platform, gender, email, birth_Year, nickname, created_at, " +
                "language, fcm_token, is_notification_enabled, hallabong, total_steps, " +
                "received_signup_bonus, total_referrals) " +
                "VALUES (false, 'APP', 'MALE', ?, '2000', ?, NOW(), 'KOREAN', ?, true, 0, 0, false, 0)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                        ps.setString(1, "bulkseed_" + i + "@it.test");
                        ps.setString(2, "bnick" + i);
                        ps.setString(3, "valid-fcm-token-seed-" + i); // "valid-fcm-token-seed-0" = 22자 > 20 ✓
                    }
                    @Override
                    public int getBatchSize() { return n; }
                });
    }

    /**
     * 단건 INSERT 방식 재현: 대상마다 jdbcTemplate.update() 1회.
     * 옛 방식(사용자별 insertIfNotDuplicate 호출)의 성능 기준선.
     */
    private void singleInsertAll(List<ReminderTarget> targets, Set<Long> attendedIds, LocalDate date) {
        String dedupKey = "attendance:" + date;
        LocalDateTime now = LocalDateTime.now();
        for (ReminderTarget t : targets) {
            if (!attendedIds.contains(t.getId()) && NotificationService.isValidToken(t.getFcmToken())) {
                jdbcTemplate.update(
                        "INSERT IGNORE INTO notification_outbox " +
                        "(user_id, fcm_token, title, body, type, dedup_key, status, retry_count, " +
                        "next_retry_at, created_at) " +
                        "VALUES (?, ?, '제주데이', '아직 오늘 출석하지 않으셨어요! 한라봉 받으러 오세요', " +
                        "?, ?, 'PENDING', 0, ?, ?)",
                        t.getId(), t.getFcmToken(),
                        NotificationType.ATTENDANCE.name(), dedupKey,
                        now, now);
            }
        }
    }

    private void truncateOutbox() {
        jdbcTemplate.execute("DELETE FROM notification_outbox");
    }

    private long median(long[] arr) {
        long[] sorted = arr.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
