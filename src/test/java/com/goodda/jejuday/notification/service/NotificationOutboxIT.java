package com.goodda.jejuday.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.goodda.jejuday.notification.entity.NotificationType;
import com.goodda.jejuday.notification.repository.NotificationOutboxRepository;
import com.goodda.jejuday.notification.service.OutboxBulkInserter.BulkOutboxRow;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.SendResponse;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
 * 알림 Outbox 시스템 통합 테스트.
 * 실제 MySQL 8.0(Testcontainers)으로 다음 세 명제를 증명한다.
 *
 * <ul>
 *   <li>IT-1: PROCESSING stuck row → sweeper 복구 → poll → SENT, 유실 0건</li>
 *   <li>IT-2: 동일 dedup 키 2회 적재 시 DB에 1건만 존재 (INSERT IGNORE 멱등성)</li>
 *   <li>IT-3: 동시 claimBatch() 두 스레드가 같은 row를 선점하지 않음 (SKIP LOCKED)</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("it")
class NotificationOutboxIT {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxIT.class);

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    // --- Mock 빈 (FcmGateway만 mock — 스펙 요구) ---
    @MockitoBean
    FirebaseMessaging firebaseMessaging;  // FirebaseConfig.firebaseMessaging() 초기화 방지

    @MockitoBean
    FcmGateway fcmGateway;               // 책임 경계 밖(구글 서버), 호출 관찰용

    @SuppressWarnings("rawtypes")
    @MockitoBean
    RedisTemplate redisTemplate;         // Redis 실연결 불필요

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;  // ChallengeRecCacheService 의존 만족

    // --- 실제 빈 (DB는 mock 없음) ---
    @Autowired
    OutboxPoller poller;

    @Autowired
    OutboxTransactionHelper txHelper;

    @Autowired
    OutboxBulkInserter bulkInserter;

    @Autowired
    NotificationOutboxRepository outboxRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws FirebaseMessagingException {
        jdbcTemplate.execute("DELETE FROM notification_outbox");
        stubFcmSuccess();
    }

    // -----------------------------------------------------------------------
    // IT-1. 크래시 복구 → 유실 0건
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("IT-1: PROCESSING stuck 50건 → sweeper 복구 → poll → SENT 50건, 유실 0건")
    void IT1_크래시복구_유실0건() throws Exception {
        int N = 50;
        LocalDateTime stuckAt = LocalDateTime.now().minusMinutes(3);

        // 1. PROCESSING 상태, processing_started_at = 3분 전 row 50건 실제 INSERT
        insertProcessingRows(N, stuckAt);
        assertProcessingCount(N);

        // 2. sweepStuckProcessing() 실행
        poller.sweepStuckProcessing();

        // 3. N건이 모두 PENDING + processing_started_at=NULL로 복구됐는지 SELECT 단언
        Long recoveredCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE status='PENDING' AND processing_started_at IS NULL",
                Long.class);
        assertThat(recoveredCount)
                .as("sweeper가 PROCESSING→PENDING 복구한 건수")
                .isEqualTo(N);

        // 4. poll() 실행 → FcmGateway.sendEach() 호출 확인
        poller.poll();
        verify(fcmGateway, atLeastOnce()).sendEach(anyList());

        // 5. 최종 N건 모두 SENT 확인 (배경 스케줄러가 일부 먼저 처리해도 합산 N건 성립)
        Long sentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE status='SENT'",
                Long.class);
        assertThat(sentCount)
                .as("최종 SENT 건수 — 유실 0건 증명")
                .isEqualTo(N);

        log.info("[IT-1] PROCESSING {}건 → sweeper 복구 → SENT {}건, 유실 0건", N, sentCount);
    }

    // -----------------------------------------------------------------------
    // IT-2. 멱등성 → 중복 발송 0건
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("IT-2: 동일 dedup 조합 2회 INSERT → DB 1건 (INSERT IGNORE 멱등성)")
    void IT2_멱등성_중복발송0건() {
        Long userId = 1L;
        String dedupKey = "attendance:2024-01-01";
        String type = NotificationType.ATTENDANCE.name();

        List<BulkOutboxRow> rows = List.of(new BulkOutboxRow(
                userId,
                "valid-fcm-token-longer-than-20-chars",
                "제주데이",
                "테스트 메시지",
                type,
                dedupKey
        ));

        // 1차 INSERT
        int firstInsert = bulkInserter.insertChunk(rows);

        // 2차 INSERT (완전히 동일한 dedup 조합)
        int secondInsert = bulkInserter.insertChunk(rows);

        // INSERT IGNORE가 2차 중복을 차단했는지 assert (반환값 0)
        assertThat(secondInsert)
                .as("INSERT IGNORE: 2차 삽입 건수 (중복 무시)")
                .isEqualTo(0);
        assertThat(firstInsert).as("1차 삽입 건수").isEqualTo(1);

        // DB에 정확히 1건만 존재
        Long dbCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE user_id=? AND type=? AND dedup_key=?",
                Long.class, userId, type, dedupKey);
        assertThat(dbCount)
                .as("DB 실제 row 수 — 중복 발송 0건 증명")
                .isEqualTo(1L);

        log.info("[IT-2] 1차 삽입={}, 2차 삽입={}, DB row 수={}", firstInsert, secondInsert, dbCount);
    }

    // -----------------------------------------------------------------------
    // IT-3. 동시 폴러 안전성 → 중복 선점 0건 (FOR UPDATE SKIP LOCKED)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("IT-3: 동시 claimBatch() 두 스레드 → 교집합 0건 (SKIP LOCKED)")
    void IT3_동시폴러_중복선점0건() throws InterruptedException {
        int N = 100;
        insertPendingRows(N);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        List<Long> batch1Ids = Collections.synchronizedList(new ArrayList<>());
        List<Long> batch2Ids = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                startLatch.await();
                txHelper.claimBatch().forEach(o -> batch1Ids.add(o.getId()));
            } catch (Exception e) {
                log.error("Thread1 error", e);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                txHelper.claimBatch().forEach(o -> batch2Ids.add(o.getId()));
            } catch (Exception e) {
                log.error("Thread2 error", e);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown(); // 동시 시작 신호
        doneLatch.await();
        executor.shutdown();

        // 두 배치의 교집합이 0이어야 한다 (SKIP LOCKED 동작 증명)
        Set<Long> intersection = new HashSet<>(batch1Ids);
        intersection.retainAll(new HashSet<>(batch2Ids));
        assertThat(intersection)
                .as("두 스레드가 동시에 선점한 row 교집합 — 0이어야 함")
                .isEmpty();

        // DB에서 PROCESSING row 수 == 두 스레드가 선점한 총 건수
        Long processingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE status='PROCESSING'",
                Long.class);
        int totalClaimed = batch1Ids.size() + batch2Ids.size();
        assertThat(processingCount)
                .as("DB PROCESSING 건수 == 두 스레드 합산 선점 건수")
                .isEqualTo((long) totalClaimed);

        log.info("[IT-3] Thread1={}건, Thread2={}건, 교집합={}건, DB PROCESSING={}건",
                batch1Ids.size(), batch2Ids.size(), intersection.size(), processingCount);
    }

    // -----------------------------------------------------------------------
    // 헬퍼
    // -----------------------------------------------------------------------

    /**
     * FcmGateway.sendEach()를 호출하면 배치 크기에 맞는 전건 성공 BatchResponse를 반환한다.
     * 배경 스케줄러 poll()이 실행돼도 NPE 없이 SENT 처리되어 테스트 단언을 깨지 않는다.
     * doAnswer 패턴: checked FirebaseMessagingException을 when() 구문에서 선언할 필요 없음.
     */
    private void stubFcmSuccess() throws FirebaseMessagingException {
        doAnswer(inv -> {
            int n = ((List<?>) inv.getArgument(0)).size();
            SendResponse sr = mock(SendResponse.class);
            when(sr.isSuccessful()).thenReturn(true);
            when(sr.getMessageId()).thenReturn("msg-id");

            BatchResponse br = mock(BatchResponse.class);
            when(br.getResponses()).thenReturn(Collections.nCopies(n, sr));
            when(br.getSuccessCount()).thenReturn(n);
            when(br.getFailureCount()).thenReturn(0);
            return br;
        }).when(fcmGateway).sendEach(anyList());
    }

    private void insertProcessingRows(int n, LocalDateTime processingStartedAt) {
        LocalDateTime now = LocalDateTime.now();
        // next_retry_at은 MySQL의 now()와 시간대 무관하게 항상 과거가 되도록 고정 과거 시각 사용
        LocalDateTime pastRetryAt = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
        jdbcTemplate.batchUpdate(
                "INSERT INTO notification_outbox " +
                "(user_id, fcm_token, title, body, type, dedup_key, status, retry_count, " +
                "next_retry_at, created_at, processing_started_at) " +
                "VALUES (?, 'valid-fcm-token-longer-than-20', '제주데이', '테스트', " +
                "'ATTENDANCE', ?, 'PROCESSING', 0, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                        ps.setLong(1, i + 1L);
                        ps.setString(2, "stuck-dedup-" + i);
                        ps.setObject(3, pastRetryAt);
                        ps.setObject(4, now);
                        ps.setObject(5, processingStartedAt);
                    }
                    @Override
                    public int getBatchSize() { return n; }
                });
    }

    private void insertPendingRows(int n) {
        LocalDateTime now = LocalDateTime.now();
        // next_retry_at은 MySQL의 now()와 시간대 무관하게 항상 과거가 되도록 고정 과거 시각 사용
        LocalDateTime pastRetryAt = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
        jdbcTemplate.batchUpdate(
                "INSERT INTO notification_outbox " +
                "(user_id, fcm_token, title, body, type, dedup_key, status, retry_count, " +
                "next_retry_at, created_at) " +
                "VALUES (?, 'valid-fcm-token-longer-than-20', '제주데이', '테스트', " +
                "'ATTENDANCE', ?, 'PENDING', 0, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws java.sql.SQLException {
                        ps.setLong(1, i + 1L);
                        ps.setString(2, "pending-dedup-" + i);
                        ps.setObject(3, pastRetryAt);
                        ps.setObject(4, now);
                    }
                    @Override
                    public int getBatchSize() { return n; }
                });
    }

    private void assertProcessingCount(int expected) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE status='PROCESSING'",
                Long.class);
        assertThat(count).as("시드 PROCESSING 건수").isEqualTo(expected);
    }
}
