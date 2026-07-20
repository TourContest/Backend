package com.goodda.jejuday.pay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.goodda.jejuday.auth.entity.Gender;
import com.goodda.jejuday.auth.entity.Language;
import com.goodda.jejuday.auth.entity.Platform;
import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.common.exception.InsufficientHallabongException;
import com.goodda.jejuday.pay.entity.LedgerReason;
import com.goodda.jejuday.pay.entity.PointLedger;
import com.goodda.jejuday.pay.repository.PointLedgerRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Step 6 — 포인트 원장(Point Ledger) 증명 테스트.
 *
 * <p>멱등성 / 정합성 / 원자성 / 보정 배치를 Testcontainers MySQL 위에서 실제 동시성으로 검증한다.
 * StepDaily 첫 기록 동시성은 StepServiceConcurrencyIT에서 별도로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("it")
@DisplayName("포인트 원장 정합성 증명 (멱등성 / 정합성 폭격 / 원자성 / 보정)")
class PointLedgerIntegrationTest {

    @Autowired private PointLedgerService pointLedgerService;
    @Autowired private PointLedgerRepository ledgerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PointLedgerReconciliationJob reconciliationJob;

    @MockitoBean private FirebaseMessaging firebaseMessaging;
    @MockitoBean private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockitoBean private org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private Long userId;

    @BeforeEach
    void setup() {
        cleanUpTables();

        String uniqueBase = Long.toString(System.nanoTime(), 36);
        User user = userRepository.save(User.builder()
                .platform(Platform.APP)
                .gender(Gender.MALE)
                .email(uniqueBase + "@test.com")
                .birthYear("1990")
                .nickname("u" + uniqueBase)
                .language(Language.KOREAN)
                .createdAt(LocalDateTime.now())
                .hallabong(0)
                .build());
        this.userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        cleanUpTables();
    }

    private void cleanUpTables() {
        ledgerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private int hallabongOf(Long id) {
        return userRepository.findById(id).orElseThrow().getHallabong();
    }

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @Test
        @DisplayName("동일 requestId로 100회 병렬 재시도 → 원장 1건, 잔액에 1회분만 반영")
        void 동일_요청ID_100회_병렬_재시도_1회만_반영() throws InterruptedException {
            String idemKey = userId + ":STEP_CONVERT:" + UUID.randomUUID();
            int attempts = 100;
            int amount = 100;

            CountDownLatch ready = new CountDownLatch(attempts);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(attempts);
            AtomicInteger recordedTrueCount = new AtomicInteger();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < attempts; i++) {
                    executor.submit(() -> {
                        ready.countDown();
                        try {
                            start.await();
                            if (pointLedgerService.record(userId, amount, LedgerReason.STEP_CONVERT, null, idemKey)) {
                                recordedTrueCount.incrementAndGet();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                ready.await();
                start.countDown();
                done.await();
            }

            // 정확히 하나의 호출만 실제로 반영되고(record()==true), 나머지 99회는 멱등 키 중복으로 무시된다.
            assertThat(recordedTrueCount.get()).isEqualTo(1);
            assertThat(ledgerRepository.count()).isEqualTo(1);
            assertThat(hallabongOf(userId)).isEqualTo(amount);
        }
    }

    @Nested
    @DisplayName("정합성 폭격")
    class ConsistencyBombardment {

        @Test
        @DisplayName("적립/차감 혼합 동시 50건 → SUM(ledger) == 잔액, 음수 잔액 0건")
        void 적립_차감_혼합_동시_50건_정합성_유지() throws InterruptedException {
            int openingBalance = 100_000;
            pointLedgerService.record(userId, openingBalance, LedgerReason.OPENING_BALANCE, null,
                    userId + ":OPENING_BALANCE");

            int creditThreads = 25;
            int debitThreads = 25;
            int creditAmount = 40;
            int debitAmount = 20;
            int totalThreads = creditThreads + debitThreads;

            CountDownLatch ready = new CountDownLatch(totalThreads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(totalThreads);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < creditThreads; i++) {
                    String idemKey = userId + ":CHALLENGE:" + i;
                    executor.submit(() -> {
                        ready.countDown();
                        try {
                            start.await();
                            pointLedgerService.record(userId, creditAmount, LedgerReason.CHALLENGE_AWARD, null, idemKey);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                for (int i = 0; i < debitThreads; i++) {
                    String idemKey = String.valueOf(1_000_000L + i); // 상품교환 idemKey = exchangeId 흉내
                    executor.submit(() -> {
                        ready.countDown();
                        try {
                            start.await();
                            pointLedgerService.record(userId, -debitAmount, LedgerReason.PRODUCT_EXCHANGE, null, idemKey);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                ready.await();
                start.countDown();
                done.await();
            }

            int expectedBalance = openingBalance + creditThreads * creditAmount - debitThreads * debitAmount;
            List<PointLedger> allLedgerRows = ledgerRepository.findAll();
            int ledgerSum = allLedgerRows.stream().mapToInt(PointLedger::getAmount).sum();
            int finalBalance = hallabongOf(userId);

            assertThat(finalBalance).isEqualTo(expectedBalance);
            assertThat(ledgerSum)
                    .as("원장 합계와 실제 잔액이 항상 일치해야 한다 (원장이 정본)")
                    .isEqualTo(finalBalance);
            assertThat(finalBalance).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("원자성")
    class Atomicity {

        @Test
        @DisplayName("잔액 부족으로 record() 실패 시 원장 insert도 함께 롤백된다")
        void 잔액부족시_원장insert도_함께_롤백() {
            String idemKey = userId + ":PRODUCT_EXCHANGE_FAIL_TEST";

            assertThatThrownBy(() -> pointLedgerService.record(userId, -100, LedgerReason.PRODUCT_EXCHANGE, null, idemKey))
                    .isInstanceOf(InsufficientHallabongException.class);

            // 원장 insert 자체는 record() 메서드 안에서 먼저 일어났지만, 같은 트랜잭션이므로
            // 이후 잔액 UPDATE가 실패(0행)해 예외가 터지면 원장 insert까지 전부 롤백되어야 한다.
            assertThat(ledgerRepository.existsByIdempotencyKey(idemKey)).isFalse();
            assertThat(hallabongOf(userId)).isZero();
        }
    }

    @Nested
    @DisplayName("보정 배치")
    class Reconciliation {

        @Test
        @DisplayName("인위적으로 잔액을 원장과 어긋나게 만들면 보정 배치가 원장 합계로 교정한다")
        void 드리프트_주입_보정_배치가_교정한다() {
            pointLedgerService.record(userId, 500, LedgerReason.ATTENDANCE, null, userId + ":ATTENDANCE:seed");

            // 원장을 거치지 않고 직접 잔액을 훼손 — 버그나 수동 DB 조작으로 인한 드리프트를 흉내낸다.
            // (save()는 SimpleJpaRepository가 자체적으로 @Transactional이므로 테스트 메서드에
            // 별도 트랜잭션이 없어도 안전하게 실행된다 — setHallabongExact은 @Modifying이라 불가)
            User corrupted = userRepository.findById(userId).orElseThrow();
            corrupted.setHallabong(9_999);
            userRepository.save(corrupted);
            assertThat(hallabongOf(userId)).isEqualTo(9_999);

            reconciliationJob.reconcile();

            assertThat(hallabongOf(userId))
                    .as("보정 배치 이후에는 원장 합계(500)로 교정되어야 한다")
                    .isEqualTo(500);
        }
    }
}
