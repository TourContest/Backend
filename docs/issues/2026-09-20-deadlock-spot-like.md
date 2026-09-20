# 부하 중 `POST /api/spots/{id}/like` 데드락 5xx

## 기록만 (원인 분석 결과와 재현 조건). 수정은 `spot.like_count` 원자화 커밋에 포함됨 (아래 「조치」)

## 관찰
승격 경로 드리프트 측정(k6, 100 TPS, 스팟 3,000개, 좋아요 55%/취소 5%/댓글 40%) 10회 중 2회에서 5xx 1건씩.
둘 다 `POST /api/spots/{id}/like`:

| 시각 | 실행 | 응답 |
|---|---|---|
| 2026-09-20 09:09:22 | kill -9 시나리오 3회차의 장애 전 구간 | 500, 1,053ms |
| 2026-09-20 09:21:07 | Redis 재시작 시나리오 1회차의 장애 전 구간 | 500, 349ms |

앱 로그 (`GlobalExceptionHandler: 처리되지 않은 예외 발생`):
```
org.springframework.dao.CannotAcquireLockException: could not execute batch [Deadlock found when trying to get lock; try restarting transaction]
  [update spot set address=?,area_code=?,category_gr...
Caused by: org.hibernate.exception.LockAcquisitionException
Caused by: java.sql.BatchUpdateException: Deadlock found when trying to get lock
Caused by: com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException: Deadlock found when trying to get lock
```

## InnoDB `LATEST DETECTED DEADLOCK` (09:21:07)
두 트랜잭션이 **같은 spot 행**(PRIMARY)에 대해:

- (1) TRX 162449: `lock mode S locks rec but not gap` 보유 → `lock_mode X locks rec but not gap` 대기
- (2) TRX 162448: `lock mode S locks rec but not gap` 보유 → `lock_mode X locks rec but not gap` 대기
- 대기 문장은 둘 다 Hibernate 의 **전체 컬럼** `update spot set address=null,area_code=null,category_group_code=null,... where id=?`
- (2) 가 롤백 대상 → 해당 요청 500

## 원인
같은 스팟에 대한 두 좋아요 요청이 동시에 진행되면:
1. 둘 다 `spot_likes` INSERT 의 FK 검사(spot_id → spot.id)로 스팟 행에 **S-락**을 잡는다
2. 이어서 `likeSpot` 이 `spot.setLikeCount(get+1); spotRepository.save(spot)` 로 스팟 행 **X-락**(UPDATE)을 요청
3. 서로 상대의 S-락이 풀리기를 기다려 데드락 (S → X 승격 데드락)

## 재현 조건
- 같은 스팟에 서로 다른 유저(또는 같은 유저)의 좋아요/취소가 **수 ms 이내에 동시에** 들어올 것
- 부하 테스트에서는 3,000개 스팟에 100 TPS 라 자연 발생은 드물었다(10회 실행 중 2건).
- 결정적 재현: 같은 스팟에 16개 스레드로 동시에 `likeSpot` → **수정 전 16건 중 4건만 성공, 12건 데드락 실패**
  (`SpotLikeCountAtomicityIT#동시_좋아요는_증가분을_잃지_않는다`). 인기 글에 좋아요가 몰릴수록 확률이 커진다.

## 조치
`9faf477`: 좋아요/취소 트랜잭션의 첫 문장으로 스팟 행 X-락(`findByIdForUpdate`)을 잡아 락 순서를 통일하고,
like_count 를 원자적 UPDATE 로 바꾸며 Spot 에 `@DynamicUpdate` 를 적용.
동시 16건 재현 테스트는 통과하지만, **k6 부하에서 5xx 가 사라졌는지는 아직 측정하지 않았다** (Phase 5 재측정 대상).

## 참고 (미해결, 별개)
댓글 작성 경로(`createComment`)도 `reply` INSERT 의 FK 검사로 같은 종류의 S-락을 잡는다. 이번 부하에서는 관찰되지 않았고 확인하지 않았다.
