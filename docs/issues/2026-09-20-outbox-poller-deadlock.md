# 알림 outbox 폴러의 배치 UPDATE 데드락 (승격 알림 폭주 직후)

## 기록만. 수정하지 않음. 좋아요/댓글 경로와 무관.

## 관찰
옵션 Z 재측정(kill -9 시나리오 2회차, 2026-09-20 10:58:39)에서 앱 로그에 `Deadlock found` 5줄. 좋아요·댓글 API 의 5xx 는 0건이고,
스케줄러 스레드가 낸 것이다.

```
10:58:30  SpotPromotionService: POST->SPOT=1278           ← 승격 1,278건이 알림/outbox 를 한꺼번에 적재
10:58:39  MessageBroker-1  OutboxTransactionHelper: FCM 배치 결과: 성공=500/500
10:58:39  ERROR  SqlExceptionHelper: Deadlock found when trying to get lock; try restarting transaction
10:58:39  ERROR  TaskUtils$LoggingErrorHandler: Unexpected error occurred in scheduled task
          CannotAcquireLockException: could not execute batch [update notification_outbox set body=?,dedup_key=?,failure_reason=?,fcm_token=?,next_retry_at=?,processed_at=?,processing_started_at=?, ...
```

## 조건
- `OutboxPoller.poll()` 의 TX2(`OutboxTransactionHelper.applyResults`, 500건 `saveAll` 배치 UPDATE)가, 같은 시각에 다른 트랜잭션(승격 알림의
  `INSERT IGNORE INTO notification_outbox`)과 같은 테이블 행/인덱스 락을 서로 반대 순서로 잡을 때.
- 승격이 수천 건의 알림을 몰아 적재한 직후 폴러가 첫 배치를 처리하는 구간에서 발생. 이번 10회 재측정 중 1회, 이전 Phase 1 의 10회에서는 관찰되지 않았다.

## 아직 모르는 것
- 배치 크기를 100 → 500 으로 키운 변경(`81094a5`)과의 관련: 배치가 커서 락 범위가 넓어졌을 가능성이 있으나 **확인하지 않았다**.
- 데드락 후 해당 배치가 어떻게 복구되는지: 폴러가 예외로 끝나 행이 `PROCESSING` 으로 남는다면 sweeper(2분 후 PENDING 복구)가 처리할 것으로 보이나
  실제로 확인하지 않았다.
- 증거 부족: InnoDB 데드락 보고서는 이 건에 대해 수집하지 못했다 (이후 실행이 덮어씀).

## 다음에 볼 것
승격 알림 적재 중 폴러 실행을 겹치는 재현, `applyResults` 의 UPDATE 순서(id 정렬 여부), 배치 크기별 발생 빈도.
