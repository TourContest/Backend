package com.goodda.jejuday.steps.service;

import com.goodda.jejuday.auth.entity.User;
import com.goodda.jejuday.auth.repository.UserRepository;
import com.goodda.jejuday.common.exception.InsufficientStepsException;
import com.goodda.jejuday.notification.service.NotificationFactory;
import com.goodda.jejuday.notification.service.NotificationService;
import com.goodda.jejuday.pay.entity.LedgerReason;
import com.goodda.jejuday.pay.service.PointLedgerService;
import com.goodda.jejuday.steps.dto.PointStatusResponse;
import com.goodda.jejuday.steps.dto.StepRequestDto;
import com.goodda.jejuday.steps.entity.MoodGrade;
import com.goodda.jejuday.steps.entity.StepDaily;
import com.goodda.jejuday.steps.repository.StepDailyRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StepService {

    private final StepDailyRepository stepDailyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final DailyStartBonusService dailyStartBonusService;
    private final PointLedgerService pointLedgerService;

    private static final int MAX_DAILY_STEPS = 20_000;
    private static final int DAILY_GOAL_STEPS = 20_000; // 일일 목표 걸음수
    private static final int MAX_DAILY_POINTS = 2000;
    private static final int POINT_CONVERSION_RATE = 10; // 10걸음당 1포인트

    // 교환 제한 상수 추가
    private static final int MAX_DAILY_EXCHANGES = 20;           // 일일 최대 교환 횟수
    private static final int MAX_SINGLE_EXCHANGE = 100;         // 한 번에 최대 교환 포인트
    private static final int MIN_EXCHANGE_UNIT = 10;            // 최소 교환 단위

    @Transactional
    public void recordSteps(Long userId, StepRequestDto dto) {
        User user = getUser(userId);
        LocalDate today = LocalDate.now();

        // 락 순서: users 행 X-락을 첫 쓰기 문장으로 잡는다. 아래 step_daily INSERT IGNORE 의 FK 검사가 users 행에 S-락을 잡은 뒤
        // 이 UPDATE 가 X-락으로 올리려 하면, 같은 유저의 동시 업로드끼리 서로 S-락을 쥔 채 기다려 데드락이 난다
        // (동시 8건 재현에서 3~5건 실패). 사용자 총 걸음수는 원자적 UPDATE 라 이 위치로 옮겨도 의미는 같다.
        userRepository.incrementTotalSteps(userId, dto.stepCount());

        // 동시 최초 기록 레이스 방지 — orElseGet(save) 대신 INSERT IGNORE로 유니크 제약 위반을
        // 예외 없이 흡수 (제약 위반 예외는 트랜잭션을 rollback-only로 마킹시킨다)
        stepDailyRepository.insertIfAbsent(userId, today);
        StepDaily todayRecord = stepDailyRepository.findByUserAndDate(user, today).orElseThrow();

        // 첫 걸음수 기록 시 시작 보너스 자동 적용
        if (!todayRecord.isStartBonusApplied()) {
            long bonusSteps = dailyStartBonusService.applyStartBonus(userId);
            if (bonusSteps > 0) {
                log.info("첫 걸음수 기록 시 시작 보너스 자동 적용: 사용자={}, 보너스={}보",
                        userId, bonusSteps);
                // todayRecord는 applyStartBonus에서 이미 업데이트됨
                todayRecord = stepDailyRepository.findByUserAndDate(user, today).orElse(todayRecord);
            }
        }

        // 오늘 걸음수 기록 — 걸음 제출 자체는 하루 상한(20,000보)이 피해를 제한하므로 원장/멱등
        // 대상에 넣지 않는다 (전환은 멱등 필수, 제출은 캡 수용 — 가이드 결정사항)
        // 원자적 가산: DB 가 total_steps + n 을 계산한다. 바로 이어 같은 트랜잭션에서 읽은 값은 이 UPDATE 가 잡은 행 락 덕분에
        // "가산 직후 값"이 정확하고, 그래서 previous = current - n 도 정확하다 → 2만보를 넘긴 요청이 동시에 여러 개여도 정확히 1개다
        // (엔티티 totalSteps += n 은 동시 요청의 가산분을 덮어쓰고 목표 알림 send 를 여러 번 부르게 했다).
        stepDailyRepository.addSteps(todayRecord.getId(), dto.stepCount());
        long currentSteps = stepDailyRepository.findTotalStepsById(todayRecord.getId());
        long previousSteps = currentSteps - dto.stepCount();

        // 2만보 달성 체크 및 알림 전송
        checkAndSendGoalAchievementNotification(user, previousSteps, currentSteps, today);

        log.info("걸음수 기록 완료: 사용자={}, 추가걸음={}, 오늘총걸음={}, 시작보너스={}보",
                userId, dto.stepCount(), currentSteps, todayRecord.getStartBonusSteps());
    }

    // 시작 보너스 수동 적용 API (필요시)
    @Transactional
    public long applyDailyStartBonus(Long userId) {
        return dailyStartBonusService.applyStartBonus(userId);
    }

    // 시작 보너스 적용 가능 여부 확인
    @Transactional(readOnly = true)
    public boolean canApplyStartBonus(Long userId) {
        return dailyStartBonusService.canApplyStartBonus(userId);
    }

    // 오늘의 시작 보너스 조회
    @Transactional(readOnly = true)
    public long getTodayStartBonus(Long userId) {
        User user = getUser(userId);
        LocalDate today = LocalDate.now();

        return stepDailyRepository.findByUserAndDate(user, today)
                .map(StepDaily::getStartBonusSteps)
                .orElse(0L);
    }

    private void checkAndSendGoalAchievementNotification(User user, long previousSteps,
                                                         long currentSteps, LocalDate date) {
        // 이전에는 2만보 미달성, 현재는 2만보 달성한 경우에만 알림 전송
        if (previousSteps < DAILY_GOAL_STEPS && currentSteps >= DAILY_GOAL_STEPS) {
            try {
                String message = String.format("오늘 목표 2만보 달성! 현재 %s보를 걸었어요! 대단해요!",
                        String.format("%,d", currentSteps));

                notificationService.send(NotificationFactory.step(user, message));

                log.info("2만보 달성 알림 전송: 사용자={}, 걸음수={}", user.getId(), currentSteps);
            } catch (Exception e) {
                log.error("2만보 달성 알림 전송 실패: 사용자={}, 에러={}", user.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public int convertStepsToPoints(Long userId, int requestedPoints, String requestId) {
        // 기본 유효성 검사
        if (requestedPoints <= 0 || requestedPoints % MIN_EXCHANGE_UNIT != 0) {
            throw new IllegalArgumentException("포인트는 10 단위로만 전환 가능합니다.");
        }

        // 한 번에 교환 가능한 최대 포인트 체크
        if (requestedPoints > MAX_SINGLE_EXCHANGE) {
            throw new IllegalArgumentException("한 번에 최대 " + MAX_SINGLE_EXCHANGE + "포인트까지만 교환 가능합니다.");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId는 필수입니다.");
        }

        // 멱등 키: userId:STEP_CONVERT:requestId (클라이언트 생성 requestId 재시도 흡수)
        String idemKey = userId + ":STEP_CONVERT:" + requestId;

        // 재전송 fast path — 이미 처리된 requestId 면 아무 것도 바꾸지 않고 당시 적립분을 그대로 돌려준다.
        // 아래 한도 사전검사보다 먼저 봐야, 원 요청이 한도를 다 쓴 뒤 도착한 정상 재시도가 거절되지 않는다.
        Optional<Integer> recorded = pointLedgerService.findRecordedAmount(idemKey);
        if (recorded.isPresent()) {
            log.debug("포인트 전환 재전송 무시(멱등): 사용자={}, requestId={}", userId, requestId);
            return recorded.get();
        }

        User user = getUser(userId);
        LocalDate today = LocalDate.now();

        StepDaily todayRecord = stepDailyRepository.findByUserAndDate(user, today)
                .orElseThrow(() -> new IllegalStateException("걸음수 기록 없음"));

        // 일일 교환 횟수 제한 체크 (빠른 실패용 — 최종 판정은 tryConsumeQuota가 DB에서 원자적으로 함)
        if (todayRecord.getExchangeCount() >= MAX_DAILY_EXCHANGES) {
            throw new IllegalArgumentException("오늘 교환 횟수를 모두 사용했습니다. (최대 " + MAX_DAILY_EXCHANGES + "회)");
        }

        long convertibleSteps = Math.min(todayRecord.getTotalSteps(), MAX_DAILY_STEPS);
        int alreadyConverted = todayRecord.getConvertedPoints();

        int availablePoints = (int)(convertibleSteps / POINT_CONVERSION_RATE) - alreadyConverted;
        availablePoints = (availablePoints / MIN_EXCHANGE_UNIT) * MIN_EXCHANGE_UNIT;

        int todayLimit = MAX_DAILY_POINTS - alreadyConverted;

        int actualConvertible = Math.min(requestedPoints, Math.min(availablePoints, todayLimit));
        if (actualConvertible <= 0) {
            // 요청 형식 자체는 유효하지만 걸음수/한도가 부족한 경우 — 프론트가 메시지 문자열이 아닌
            // 전용 에러 코드(INSUFFICIENT_STEPS)로 안정적으로 감지해 사전에 버튼을 막을 수 있도록 구분한다.
            throw new InsufficientStepsException("걸음수가 부족해서 교환할 수 없습니다. (전환 가능 포인트: " + Math.max(0, availablePoints) + ")");
        }

        // 1) 멱등 INSERT 먼저 (원장 + 잔액 증가). 영향 행 0 = 같은 requestId 가 동시에 먼저 처리됨.
        //    이 경우 한도를 차감하면 포인트는 안 들어가는데 한도만 소진되므로 여기서 끝낸다.
        boolean applied = pointLedgerService.record(userId, actualConvertible, LedgerReason.STEP_CONVERT, null, idemKey);
        if (!applied) {
            log.debug("포인트 전환 동시 재전송 무시(멱등): 사용자={}, requestId={}", userId, requestId);
            return actualConvertible; // 경합 상대 트랜잭션의 행은 이 트랜잭션 스냅샷에 안 보일 수 있어 계산값을 돌려준다
        }

        // 2) 신규 요청일 때만 한도/횟수를 DB에서 원자적으로 재검증하며 소진.
        //    0행이면 예외로 이 트랜잭션 전체를 롤백 → 1)에서 넣은 원장/잔액 증가도 함께 취소된다.
        int updatedRows = stepDailyRepository.tryConsumeQuota(
                todayRecord.getId(), actualConvertible, MAX_DAILY_EXCHANGES, MAX_DAILY_POINTS);
        if (updatedRows == 0) {
            throw new InsufficientStepsException("걸음수가 부족해서 교환할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        checkAndRewardMoodUpgrade(user);

        // record()/tryConsumeQuota()가 벌크 UPDATE로 반영한 값은 이미 로드된 user/todayRecord에
        // 즉시 반영되지 않으므로, 로그용으로만 스칼라 프로젝션으로 최신 잔액을 다시 읽는다.
        Integer totalHallabong = userRepository.findHallabongById(userId);
        log.info("포인트 전환 완료: 사용자={}, 전환포인트={}, 총보유포인트={}, 교환횟수={}/{}",
                userId, actualConvertible, totalHallabong,
                todayRecord.getExchangeCount() + 1, MAX_DAILY_EXCHANGES);

        return actualConvertible;
    }

    public int getRemainingConvertiblePoints(User user) {
        StepDaily todayRecord = stepDailyRepository.findByUserAndDate(user, LocalDate.now())
                .orElse(null);

        if (todayRecord == null) return MAX_DAILY_POINTS;

        // 교환 횟수 제한도 고려
        if (todayRecord.getExchangeCount() >= MAX_DAILY_EXCHANGES) {
            return 0; // 교환 횟수를 모두 사용한 경우
        }

        long steps = Math.min(todayRecord.getTotalSteps(), MAX_DAILY_STEPS);
        int alreadyConverted = todayRecord.getConvertedPoints();

        int availablePoints = (int)(steps / POINT_CONVERSION_RATE) - alreadyConverted;
        availablePoints = (availablePoints / MIN_EXCHANGE_UNIT) * MIN_EXCHANGE_UNIT;

        int todayLimit = MAX_DAILY_POINTS - alreadyConverted;

        return Math.max(0, Math.min(todayLimit, availablePoints));
    }

    // 남은 교환 횟수 조회
    @Transactional(readOnly = true)
    public int getRemainingExchangeCount(Long userId) {
        User user = getUser(userId);
        LocalDate today = LocalDate.now();

        return stepDailyRepository.findByUserAndDate(user, today)
                .map(StepDaily::getRemainingExchangeCount)
                .orElse(MAX_DAILY_EXCHANGES);
    }

    // 오늘의 교환 횟수 조회
    @Transactional(readOnly = true)
    public int getTodayExchangeCount(Long userId) {
        User user = getUser(userId);
        LocalDate today = LocalDate.now();

        return stepDailyRepository.findByUserAndDate(user, today)
                .map(StepDaily::getExchangeCount)
                .orElse(0);
    }

    private void checkAndRewardMoodUpgrade(User user) {
        MoodGrade current = user.getMoodGrade();
        if (!user.getReceivedMoodGrades().contains(current)) {
            int reward = current.getReward();
            if (reward > 0) {
                // 멱등 키: userId:MOOD_REWARD:grade — 등급당 1회. receivedMoodGrades의
                // check-then-add 레이스가 동시에 일어나도 실제 지급은 이 멱등 키로 단 1회만 보장된다.
                String idemKey = user.getId() + ":MOOD_REWARD:" + current.name();
                pointLedgerService.record(user.getId(), reward, LedgerReason.MOOD_REWARD, null, idemKey);

                // receivedMoodGrades는 표시용 캐시 — 중복 방지 책임은 위 멱등 키로 이관됐으므로
                // 이 필드 자체의 레이스는 더 이상 잔액 정합성에 영향을 주지 않는다.
                user.getReceivedMoodGrades().add(current);

                // 걸음수 전환 응답의 convertedPoints에는 안 잡히는 별도 보상이라, 알려주지
                // 않으면 유저가 "전환한 것보다 잔액이 더 늘었다"고 혼란스러워한다.
                try {
                    notificationService.send(NotificationFactory.moodGradeUp(user, current, reward));
                } catch (Exception e) {
                    log.error("무드 등급 보상 알림 전송 실패: 사용자={}, 등급={}, 에러={}",
                            user.getId(), current, e.getMessage(), e);
                }

                log.info("무드 등급 보상 지급: 사용자={}, 등급={}, 보상={}",
                        user.getId(), current, reward);
            }
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));
    }

    public Set<MoodGrade> getReceivedRewardGrades(Long userId) {
        return getUser(userId).getReceivedMoodGrades();
    }

    @Transactional(readOnly = true)
    public PointStatusResponse getPointStatus(Long userId) {
        User user = getUser(userId);
        return new PointStatusResponse(user.getHallabong(), user.getMoodGrade());
    }

    /**
     * 오늘 걸음수 조회 (디버깅용)
     */
    @Transactional(readOnly = true)
    public long getTodaySteps(Long userId) {
        User user = getUser(userId);
        LocalDate today = LocalDate.now();

        return stepDailyRepository.findByUserAndDate(user, today)
                .map(StepDaily::getTotalSteps)
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public boolean isGoalAchievedToday(Long userId) {
        return getTodaySteps(userId) >= DAILY_GOAL_STEPS;
    }
}