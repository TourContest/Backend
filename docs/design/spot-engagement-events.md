# 승격 경로 이벤트 설계안 (Phase 2)

- 상태: **설계안 (구현 전).** 코드 수정 없음.
- 브랜치: `feat/event-outbox-rabbitmq` (main 기준: fix/promotion-consistency 병합 후)
- 표기 규칙: **[측정]** 이 저장소에서 직접 잰 값, **[코드]** 코드로 확인, **[추정]** 근거 있는 추정, **[미확인]** 확인하지 못함.
  측정하지 않은 수치는 쓰지 않는다.

## 0. 먼저 읽을 것 — 핵심 판단 3가지

1. **(A)와 (C)를 만족시키면 engagement ZSet을 읽는 코드가 없어진다.** [코드]
   현재 ZSet 읽기: 승격 판정(`SpotPromotionService`)과 목록 화면의 "승격까지 좋아요 N개"(`SpotServiceImpl:113,144`) 둘뿐이다.
   (C)로 승격 판정이 DB 실측을 쓰고 (A)로 화면 계산에서 Redis를 걷어내면(3.6절), ZSet은 아무도 읽지 않는 캐시가 된다.
   → 이벤트 Outbox는 **읽는 사람이 없는 캐시를 정확하게 유지**하려는 장치가 된다. 그래서 「옵션 Z: ZSet과 이벤트 경로를 통째로 제거」(7절)를
   같이 제시한다. 아래 안 1·2는 요청하신 전제(ZSet 유지)로 작성했다. **이 결정은 6절에서 요청드린다.**
2. **RabbitMQ 안은 1GB 인스턴스에서 성립하지 않는다.** [측정] 컨테이너 유휴 129.7MiB, 영속 메시지 100 msg/s 30초 후 135.6MiB.
   현재 상한 합 956m(app 560 + mysql 220 + redis 80 + caddy 96)에서 남는 여유는 1GB 기준 68MB뿐이고, 줄일 수 있는 후보(app, mysql)는 측정상 여유가 없다(5.4절).
3. **RabbitMQ를 써도 폴링은 사라지지 않는다.** 소비자가 하나뿐이고 발행 원천이 DB Outbox라서, 릴레이가 DB를 폴링한다. 브로커는 홉 하나와 메모리를 더할 뿐이다.
   소비자를 여러 개로 늘릴 계획이 생기기 전에는 이득이 없다 (이벤트 소비자는 지금 `SpotRankingUpdater` 하나).

## 1. 현재 상태와 측정된 문제 (Phase 1 요약)

인메모리 이벤트: 좋아요·취소·댓글 커밋 → `EngagementChangedEvent` → `SpotRankingUpdater`(AFTER_COMMIT, **요청 스레드에서 동기**) → Redis ZADD 2회
(hot, engagement). [코드]

| 항목 | 측정 (100 TPS × 150초, 스팟 3,000개, 3회) |
|---|---|
| 무장애 대조군 | engagement/hot 드리프트 0건, 부당·누락 승격 0건 |
| kill -9 | engagement 드리프트 0 / 12 / 0, 부당 승격 0 / 24 / 0 |
| Redis 30초 무응답 | 39 / 32 / 30, **부당 승격 147 / 16 / 16**, 누락 1 / 2 / 1 |
| Redis 재시작(영속화 OFF) | **143~153 (전부 누락)**, **누락 승격 15~19** |
| Redis 무응답 중 like/comment API 성공률 | **3.0% / 5.2% / 15.1%** (나머지는 k6 10초 타임아웃) |
| Redis 장애 중 요청 1건 (부하 없음) | HTTP **200**, 소요 **약 2.05초**, DB 커밋됨, **ZSet 갱신 유실** (예외는 삼켜짐, 500 아님) |

원인 구조: Redis 호출이 요청 스레드에 있고(Lettuce 명령 타임아웃 2초, `application.yml:24`), 실패해도 API는 성공으로 끝나며, 유실된 갱신을 다시 시도할 곳이 없다.
스레드 고갈이 실제 원인인지는 스레드 덤프로 확인하지 않았다. [추정]

추가로 확인한 것 (EXPLAIN, 좋아요 8,187행 / 댓글 5,975행 / 스팟 3,000개) [측정]:

| 카운트 쿼리 | 실행 계획 |
|---|---|
| 현재 소비자의 좋아요 카운트 (`target_id` 기준) | 유니크 인덱스 **전체 스캔** (약 8,099행) |
| 현재 소비자의 댓글 카운트 (`content_id` 기준) | **풀 테이블 스캔** (`type: ALL`, 인덱스 없음, 약 6,088행) |
| `spot_id` 기준 좋아요 카운트 (FK 인덱스) | range 스캔, 약 12행 |
| `content_id IN (…)` 배치 댓글 카운트 | 풀 스캔 + 임시 테이블 (인덱스 없음) |

즉 **이벤트 1건마다 테이블 크기에 비례하는 스캔**이 일어난다. 합치기(3.4절)와 인덱스 추가(3.3절)가 필요한 정량적 이유다.

## 2. 목표와 수용 기준

| 목표 | 수용 기준 (측정 방법) |
|---|---|
| **(A)** Redis 장애 중에도 좋아요·댓글 API 성공률 유지, 요청 스레드에서 Redis 호출 제거 | Phase 1과 같은 k6·Redis 30초 무응답에서 like/comment 성공률이 무장애 대조군과 같다. `likeSpot`/`createComment` 경로에서 Redis 상호작용 0회(테스트에서 `RedisTemplate` 무호출 검증) |
| **(B)** 이벤트 유실 없이 Redis 복구 후 드리프트 0건, 보정 배치 없이 | Phase 1의 kill -9 / Redis 무응답 / Redis 재시작 3시나리오 종료 후 **보정 배치 실행 전** engagement 드리프트 0건. "복구 후 0건이 되기까지 시간"을 함께 기록 |
| **(C)** 승격 판정 직전 DB 실측 재확인으로 부당 승격 0건 | Phase 1의 승격 검사에서 `unjust_promotions = 0` (ZSet이 틀려도) |
| **(D)** ZSet이 비었거나 키가 없으면 DB로 재구성해 누락 승격 0건 | Redis 재시작 시나리오에서 `missed_promotions = 0`, ZSet 재구성 완료까지 시간 기록 |

## 3. 안 1 — DB 이벤트 Outbox + 폴러 소비자 (권장 후보)

### 3.1 구조

```
[요청 스레드]  좋아요/댓글 트랜잭션
   ├─ 비즈니스 변경 (spot_likes / reply / spot.like_count)
   ├─ INSERT INTO event_outbox (같은 트랜잭션)          ← 커밋과 원자적. Redis 호출 없음
   └─ afterCommit: 폴러에 wake-up 신호 (메모리 플래그, I/O 없음)

[폴러 스레드 1개]  wake-up 또는 안전망 주기(예: 5초)
   ├─ Redis 센티널 확인 → 없으면 전체 재구성 요청 (3.5절)
   ├─ 대기 중인 spotId 를 중복 제거해 최대 500개 선택
   ├─ DB 에서 좋아요/댓글 수를 배치 GROUP BY 2회로 재계산
   ├─ Redis 에 ZADD 1회(다중 멤버) — 삭제·없는 스팟은 ZREM
   └─ 성공하면 처리한 이벤트 행 삭제
```

요청 스레드가 하는 일은 DB INSERT 1건과 메모리 플래그 하나뿐이다. Redis가 죽어도 API는 영향이 없다.

### 3.2 이벤트 Outbox 테이블

```sql
CREATE TABLE event_outbox (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id        CHAR(36)     NOT NULL,                       -- UUID, 추적/멱등 (유니크)
  type            VARCHAR(40)  NOT NULL,                       -- SPOT_ENGAGEMENT_CHANGED | ENGAGEMENT_REBUILD_ALL
  aggregate_id    BIGINT       NULL,                           -- spotId (REBUILD_ALL 은 NULL)
  payload         JSON         NULL,                           -- 확장용 (현재 비움)
  status          ENUM('PENDING','FAILED') NOT NULL DEFAULT 'PENDING',
  attempts        INT          NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(6)  NOT NULL,
  created_at      DATETIME(6)  NOT NULL,
  UNIQUE KEY uk_event_id (event_id),
  KEY idx_pending (status, aggregate_id, id)
);
```

- **성공한 행은 삭제한다** (`PUBLISHED` 로 남기지 않는다). 폴링 인덱스가 작게 유지되고, 소비 지연은 Micrometer 히스토그램(`now - created_at`)으로 기록한다.
  요청하신 "발행 시각" 컬럼 대신 지표로 대체한 것이다. 감사 이력이 필요하면 삭제 대신 `processed_at` 을 두고 야간에 청소하는 안으로 바꿀 수 있다.
- `FAILED`: 독성 이벤트(재시도 5회 초과)만 남긴다. Redis 장애 같은 **인프라 실패는 attempts 를 올리지 않고** 전체 백오프한다 (3.4절).
- 마이그레이션은 `db/migrations/` 에 수동 SQL + 배포 후 확인 SQL (기존 방식). ddl-auto=update 의 조용한 실패를 피하려면 인덱스 생성 확인이 필요하다.

### 3.3 인덱스 (EXPLAIN 근거, 1절)

- `reply (content_id, depth)` 신규: 현재 댓글 카운트는 풀 스캔이다. [측정]
- 좋아요 카운트는 **`spot_id` 로 조회**한다 (FK 인덱스가 이미 있다. 스팟 좋아요는 `target_id = spot_id`). `target_id` 기준 쿼리는 인덱스 전체 스캔이다. [측정]
- 위 두 가지는 (C)의 조건부 UPDATE 서브쿼리에도 필요하다.
- 운영 데이터 크기에서의 실행 계획은 확인하지 못했다. [미확인] Phase 3에서 운영과 같은 크기의 시드로 EXPLAIN을 다시 한다.

### 3.4 소비자: 같은 spotId 합치기와 경합 제거

**처리 단위를 이벤트가 아니라 스팟으로 잡는다.** 선택·삭제 방식:

1. `maxId = SELECT MAX(id) FROM event_outbox` (이 라운드가 책임지는 상한)
2. `SELECT DISTINCT aggregate_id FROM event_outbox WHERE id <= :maxId AND status='PENDING' AND next_attempt_at <= NOW(6) LIMIT 500`
3. 스팟 500개의 좋아요/댓글 수를 각각 GROUP BY **한 번**으로 계산 (삭제된 스팟은 제외 → ZREM 대상)
4. Redis `ZADD community:engagement s1 m1 s2 m2 ...` **한 번** (파이프라인/다중 멤버)
5. 성공하면 `DELETE FROM event_outbox WHERE aggregate_id IN (...) AND id <= :maxId`

효과:

- **재조회→ZADD 경합 제거.** 한 라운드 안에서 스팟당 재계산이 한 번이고, 라운드는 직렬이다(폴러 1개, 다중 인스턴스 대비 `GET_LOCK` 또는 ShedLock으로 단일 활성 보장).
  이벤트 행이 커밋된 뒤에 선택되므로 재계산은 그 이벤트의 DB 변경 이상으로 최신이다. `maxId` 이후에 커밋된 이벤트는 다음 라운드가 처리하고,
  마지막 쓰기는 항상 가장 나중 라운드다. 그래서 오래된 값이 새 값을 덮어쓰는 일(1절 발견의 read→ZADD 경합)이 구조적으로 없다.
- **크래시 안전.** 행은 성공 뒤에만 삭제되므로 `PROCESSING` 상태나 sweeper 가 필요 없다. 재기동하면 다시 처리하고, 계산이 DB 절대값이라 멱등이다.
- **적체 후 빠른 회복.** Redis 가 오래 죽었다 살아나면 쌓인 이벤트가 몇 개든 **고유 스팟 수**(최대 3,000개, 라운드당 500개 → 6라운드)만큼만 계산한다.
  이벤트 100 TPS × 1시간 = 36만 행이 쌓여도 [추정: 행당 수십~100바이트 수준이라 수십 MB, 측정하지 않음] 계산은 스팟 수에 비례한다.
- SKIP LOCKED 를 쓰지 않는 이유: 알림 Outbox(`OutboxPoller`)는 행마다 독립이라 여러 스레드가 나눠 가져도 되지만, 여기서는 **스팟이 작업 단위**라
  두 스레드가 같은 스팟을 나눠 처리하면 위 경합이 되살아난다.

**Redis 쓰기 횟수** [추정, 측정하지 않음]: 현재 이벤트당 ZADD 2회(200 TPS 상당). 안 1은 라운드당 ZADD 1회다.
합치기 자체의 감소 효과는 분포에 좌우된다: 스팟이 균일하면 100건이 약 98개 스팟(3,000개 중 100회 추첨의 기대값)이라 **거의 줄지 않고**, 인기 글에 몰릴수록 크게 줄어든다.
큰 감소는 합치기가 아니라 **배치 1회 호출**과 **hot ZADD 제거**(4절)에서 온다.

**실패 처리**

| 상황 | 동작 |
|---|---|
| Redis 오류/타임아웃 | 행을 그대로 두고 라운드 전체를 백오프(1s→2s→…→30s 상한). attempts 를 올리지 않음. 요청 경로는 무관 |
| 스팟이 삭제/없음 | 점수를 계산하지 않고 ZREM, 이벤트는 정상 삭제 |
| 이벤트 처리 중 예외(DB 등) | 해당 라운드 롤백, 재시도. 같은 행이 5회 연속 실패하면 `FAILED` 로 격리하고 로그/지표 |
| 앱 kill -9 | 삭제 전이라 행이 남아 재기동 후 재처리 (B) |

### 3.5 (B) 드리프트 0건과 (D) 재구성

- **센티널 키** `community:engagement:epoch`: 폴러가 Redis 에 쓸 때 함께 유지한다. 폴러는 라운드마다(또는 10초마다) 센티널 `EXISTS` 를 확인하고,
  없으면 Redis 데이터가 사라졌다고 보고 `ENGAGEMENT_REBUILD_ALL` 을 요청한다. 앱 기동 시에도 한 번 확인한다.
- `REBUILD_ALL`: 최근 14일 스팟 전체(승격 후보 창)를 DB 에서 배치로 재계산해 ZADD. 3,000개 기준 6라운드.
  (전체 스팟이 아니라 창 안만 다루는 이유: 현재 정리 작업이 14일이 지난 멤버를 지우는 것과 같은 범위, `SpotPromotionService.cleanUpOldRankingData`.)
- (B): 이벤트가 DB 에 남아 있으므로 Redis 가 복구되면 밀린 것을 처리하고 끝난다. **보정 배치는 필요 없어진다.**
  다만 안전망으로 유지하되 04:00 배치가 발견한 드리프트 건수를 경고 로그로 남기는 것(기존 동작)을 그대로 둔다. 수용 기준은 배치를 돌리지 않고 0건이다.
- Redis 재시작(영속화 OFF)은 센티널까지 사라지므로 위 절차로 자동 재구성된다. 재구성 완료까지 시간은 **[미확인]** (Phase 4에서 측정).

### 3.6 (A) 요청 스레드의 Redis 호출을 전부 제거

이벤트 소비 경로만 옮기면 부족하다. **목록 조회 화면도 요청 스레드에서 Redis 를 읽는다** [코드: `SpotServiceImpl:113,144`, `executePipelined ZSCORE`].
Redis 가 죽으면 목록 API 도 2초씩 밀릴 수 있다. (부하로 재현하지는 않았다. [미확인])

- "승격까지 좋아요 N개" 는 engagement = 좋아요×3 + 댓글 이므로 **DB 값으로 계산**한다: `spot.like_count`(이번에 원자적 UPDATE 로 정확해짐)와 페이지 단위로
  이미 조회하는 댓글 수(`commentCounts`). 단, 댓글 수는 삭제 제외 정의(`countActiveGroupByContentIds`)로 바꿔 승격 점수와 일치시킨다.
- 이 변경으로 like/comment/목록 경로에서 Redis 호출이 0이 된다. 남는 요청 경로의 Redis 사용은 챌린지 추천 캐시(`ChallengeRecCacheService`)로 이번 범위 밖이다. [코드]
  그쪽도 같은 종류의 2초 대기를 가질 수 있어 별도 항목으로 남긴다 (명령 타임아웃 단축 + 서킷브레이커).

### 3.7 (C) 승격 판정 직전 DB 실측 재확인

승격 판정을 ZSet 이 아니라 DB 실측으로 한다.

1. 후보 스팟의 engagement 를 **배치 GROUP BY 2회**로 계산 (소비자와 같은 쿼리, 같은 삭제 제외 정의)
2. POST→SPOT: `engagement ≥ 10`, SPOT→CHALLENGE: DB 값 기준 상위 2개 (지금은 ZSet 순위)
3. 전이는 **조건부 UPDATE 한 문장에 판정을 포함**한다. 판정과 전이 사이에 취소가 커밋되는 틈을 없애기 위해서다:

```sql
UPDATE spot s SET s.type = 'SPOT'
WHERE s.id = :id AND s.type = 'POST'
  AND 3 * (SELECT COUNT(*) FROM spot_likes l WHERE l.spot_id = s.id AND l.target_type = 'SPOT')
    + (SELECT COUNT(*) FROM reply r WHERE r.content_id = s.id AND r.depth = 0 AND (r.is_deleted = 0 OR r.is_deleted IS NULL)) >= 10
```

영향 행 1일 때만 알림을 적재하는 기존 원자성(`transitionAndNotify`, main 병합됨)은 그대로다. 이 안에서 승격 판정은 이벤트 소비자가 아니라 **매시 크론 그대로**다.
(알림도 이미 같은 트랜잭션에서 적재되므로 이벤트로 옮기지 않는다.) ZSet 이 틀려도 부당 승격이 생기지 않으므로 (C)는 ZSet 정확도와 독립이다.

## 4. hot ZSet(`community:ranking`) 쓰기 제거

정렬에 쓰는 곳이 없다. [코드, 재확인 완료: 쓰기 3곳 / 읽기는 보정 배치·디버그 엔드포인트·결과를 버리는 `PopularityScheduler`]

- 제거 대상: `SpotRankingUpdater` 의 hot ZADD, 보정 배치의 hot 비교·수정, `cleanUpOldRankingData` 의 hot ZREM, `PopularityScheduler`(no-op), `SpotScoreCalculator.hotScore`
  (와 관련 테스트), `NotificationTestController` 의 hot 필드.
- 배포 시 `DEL community:ranking` 1회.
- 효과: 이벤트당 ZADD 2→1 (구조상 자명). 조회수가 hot 점수에 들어가 생기던 "가짜 드리프트"도 함께 사라진다 (`SpotServiceImpl:409` 조회수 증가는 이벤트를 안 냄).

## 5. 안 2 — RabbitMQ (전달 계층만 교체하는 비교안)

### 5.1 무엇이 같고 무엇이 다른가

같은 것: 이벤트 Outbox 테이블, 트랜잭션 내 적재, DB 실측 재계산, 센티널/재구성, 승격 (C), hot 제거.
다른 것: 폴러 소비자 자리에 **릴레이 + 브로커 + 리스너**가 들어간다.

```
[요청 스레드] 비즈니스 변경 + event_outbox INSERT (동일)
[릴레이]  DB 를 폴링해 스팟별로 합친 메시지를 발행 → publisher confirm 을 받은 뒤에만 행 삭제
          (커밋 직후 즉시 발행 시도 + 폴링은 누락분 안전망)
[RabbitMQ] topic exchange → 소비자별 durable 큐
[리스너]  배치로 받아 (3.4절과 같은) 스팟별 재계산 → ZADD → ack
```

### 5.2 토폴로지

- exchange `spot.events` (topic, durable), 라우팅 키 `spot.engagement.changed`
- 큐 `spot.engagement.ranking` (durable, classic 1노드), 재시도 큐 `...retry.5s` (`x-message-ttl=5000`, 만료 시 원 exchange 로 dead-letter), DLQ `...dlq`
- 메시지 persistent(delivery-mode 2), **수동 ack**, prefetch 50~100, 배치 리스너(`consumerBatchEnabled`, batchSize·receiveTimeout 지정)로 합치기를 유지
- 릴레이는 스팟별로 합쳐서 발행 → 메시지 수가 이벤트 수보다 적다. 리스너는 그래도 배치에서 한 번 더 합친다
- Redis 장애: 리스너는 즉시 재큐(nack requeue)하지 않고 재시도 큐로 보낸다. 서킷 오픈 시 리스너 컨테이너를 정지해 핫루프를 막는다
- 독성 메시지: 재시도 5회 후 DLQ → 나머지 메시지는 계속 처리

### 5.3 안 1 대비 실제로 얻는 것과 더하는 것

| | 안 1 (DB 폴러) | 안 2 (RabbitMQ) |
|---|---|---|
| DB 폴링 | 있음 (소비자가 폴링) | **있음** (릴레이가 폴링) — 사라지지 않음 |
| 홉 | DB → 폴러 → Redis | DB → 릴레이 → 브로커 → 리스너 → Redis |
| 소비자 독립성 | 소비자 1개라 무의미 | 소비자가 늘 때 유의미 (지금은 1개) |
| 재시도·DLQ | 직접 구현(백오프, FAILED 격리) | 브로커 기능 사용 (TTL 재시도 큐, DLQ) |
| 추가 컨테이너 | 없음 | RabbitMQ (측정 유휴 130~136MiB) |
| 새 실패 지점 | 없음 | 브로커 자체 장애, 발행 확인 실패 시 Outbox 잔류(안전) |

### 5.4 메모리와 1GB 배분

측정 [측정]:

| 대상 | 측정값 |
|---|---|
| RabbitMQ 3.13-alpine 유휴 | **129.7MiB** |
| 위 + 영속 메시지 100 msg/s 30초 | **135.6MiB** |
| RabbitMQ 3.13-management-alpine 유휴 | 120.8MiB (1회 측정; 플러그인 유무 차이는 이 측정으로 구분 못 함) |
| app (loadtest 튜닝 측정) | 유휴 약 505MiB, 부하 최대 524MiB (상한 560) |
| mysql (같은 측정) | 최대 189.9MiB (상한 220) |
| redis (같은 측정) | 최대 약 10MiB (상한 80) |

배분 (운영 상한은 말씀하신 값: app 560 / mysql 220 / redis 80 / caddy 96 = 956m, 인스턴스 1GB):

| 구성 | app | mysql | redis | caddy | rabbitmq | 합 | 1GB 대비 |
|---|---|---|---|---|---|---|---|
| 현재 | 560 | 220 | 80 | 96 | - | 956 | 여유 68 |
| **안 1** (그대로) | 560 | 220 | 80 | 96 | - | 956 | 여유 68 |
| 안 1 + redis 축소 (선택) | 560 | 220 | 48 | 96 | - | 924 | 여유 100 |
| **안 2** (redis 축소 + 최소 RabbitMQ) | 560 | 220 | 48 | 96 | 192 | **1,116** | **92 초과** |

- RabbitMQ 상한을 192m 로 잡은 근거: 유휴가 이미 130MiB 이므로 워터마크·페이징 여유를 두려면 그 이상이 필요하다. **[추정]** 정확한 최소치는 측정하지 못했다.
- `vm_memory_high_watermark.absolute` 는 **프로세스 전체 사용량(유휴 130MiB 포함) 기준**으로 발동한다고 알려져 있어 [추정: 문서 기반, 이 환경에서 확인 안 함],
  유휴 사용량보다 낮게 주면(예: 64MiB) 시작하자마자 발행이 막힌다. 안 2를 택한다면 컨테이너 상한 192m 아래에서 약 **150~160MiB 부근**이 상한선이고, 이 값은 Phase 3에서 실측해야 한다.
- 관리 플러그인: 켜면 메모리와 포트가 늘 수 있어(이번 측정으로는 차이 확인 불가) 운영에서는 끄고, 지표는 `rabbitmq-diagnostics`/Prometheus 플러그인이 필요하면 별도 판단한다.
- 줄일 후보: redis(측정 10MiB)만 여유가 있다. app(524/560)과 mysql(190/220)은 측정상 여유가 없고, caddy 96m 는 측정하지 않았다. [미확인]
  → 1GB 에서 안 2는 92MB 초과라서 **인스턴스를 올리지 않으면 성립하지 않는다.**
- 안 1의 추가 메모리: 폴러 스레드 1개와 배치 500건 분량 객체. **[추정]** 수 MB 이하. Phase 5에서 컨테이너별 메모리 최대값으로 확인한다.

## 6. 안 1 vs 안 2 비교 지표 (같은 k6 시나리오)

같은 시나리오: Phase 1의 engagement 부하(100 TPS × 150초) + 각 장애(kill -9 / Redis 30초 무응답 / Redis 재시작) + 무장애 대조군.

| 지표 | 정의 / 측정 방법 | 안 1 | 안 2 | 현재 기준선 |
|---|---|---|---|---|
| 소비 지연 | 이벤트 커밋 → Redis 반영. 폴러/리스너가 `now - created_at` 을 Micrometer 히스토그램으로 기록, p50/p95/max | Phase 5 측정 | Phase 5 측정 | 요청 스레드에서 동기(장애 시 유실이므로 정의 불가) |
| 폴링 쿼리 수 | 유휴 1분과 부하 구간의 `SHOW GLOBAL STATUS` `Questions`(또는 Hibernate 통계) 증분, 폴러/릴레이 기여분 | Phase 5 측정 | Phase 5 측정 (릴레이도 폴링) | 없음(이벤트 경로) |
| 컨테이너 메모리 | `docker stats` 최대/평균, 컨테이너별 (app/mysql/redis/rabbitmq) | Phase 5 측정 | Phase 5 측정 (RabbitMQ 추가) | app 최대 524MiB, mysql 190MiB, redis 10MiB |
| 1GB 배분 | 5.4절 표 + 실측 최대값으로 갱신 | 여유 68MB | 92MB 초과 | 여유 68MB |
| (A) like/comment 성공률 | Redis 무응답 30초 구간 | Phase 5 | Phase 5 | 3.0 / 5.2 / 15.1% |
| (B) 드리프트, 회복 시간 | 배치 실행 전 engagement 드리프트, 복구 후 0건까지 시간 | Phase 5 | Phase 5 | 위 1절 |
| (C)(D) 부당·누락 승격 | Phase 1의 승격 검사 | Phase 5 | Phase 5 | 위 1절 |

**설계 단계에서 이미 확정되는 것**: 폴링 쿼리는 두 안 모두 0이 아니다. 안 2가 폴링을 줄이려면 릴레이 폴링 주기를 안전망 수준(예: 10초 이상)으로 늘리고
커밋 직후 즉시 발행에 의존해야 한다. 안 1도 같은 wake-up 신호로 폴링 주기를 안전망 수준으로 늘릴 수 있어서, 이 최적화는 두 안의 차이가 아니다.

## 7. 검토 후 채택하지 않은 안

| 안 | 이유 |
|---|---|
| **옵션 Z: engagement ZSet과 이벤트 경로 전체 제거** | (A)~(D) 를 구조적으로 만족: Redis 호출 0, 드리프트 개념 자체가 없음, 승격은 DB 실측(3.7). 이벤트 Outbox·폴러·센티널·재구성 코드가 모두 불필요. **요청하신 전제와 달라 제외한 것이 아니라, 결정이 필요해 6절에서 묻는다.** 대가: 목록 화면 값은 `spot.like_count`(비정규화)와 댓글 수로 계산, 승격 배치가 후보 전체의 GROUP BY 를 매시 수행(후보 창 14일). 미래에 정렬용 랭킹 ZSet 이 필요해지면 다시 도입 |
| 소비자별 Outbox 폴러 | 소비자가 1개라 안 1과 동일. 소비자가 늘면 그때 분리 |
| Spring Modulith 이벤트 발행 기록 | JVM 내 재발행에 DB 영속화를 더하는 방식이라 안 1과 본질이 같은데 의존성이 추가된다. 폴링/합치기/센티널은 어차피 직접 구현 |
| Redis Streams | 운영 Redis 는 영속화가 꺼져 있어(`--save "" --appendonly no`) 재시작 시 스트림이 사라진다. (B) 위반 |
| Kafka | 1GB 인스턴스에서 브로커+ZooKeeper/KRaft 메모리를 감당할 수 없다. 처리량이 이유가 아니다 |

## 8. 전환 절차 (안 1 기준)

1. 배포 전 마이그레이션: `event_outbox`, `reply(content_id, depth)` 인덱스, (수동 SQL + 배포 후 확인 SQL)
2. 배포: 발행부(5곳)를 `EngagementEvents.record` 로 교체, `SpotRankingUpdater` 의 AFTER_COMMIT 리스너 제거, 폴러 추가, 목록 화면 DB 계산, 승격 DB 판정
3. 기동 직후 폴러가 센티널 부재를 감지해 `REBUILD_ALL` 자동 실행 (배포 중 유실된 인메모리 이벤트를 흡수)
4. `DEL community:ranking`
5. 롤백: 이벤트 적재는 무해하므로 이전 버전으로 되돌려도 데이터 손상 없음 (테이블은 남음)

## 9. 확인한 것 / 추정한 것 / 확인하지 못한 것

**확인한 것** [측정·코드]
- Phase 1 드리프트·승격 수치, Redis 장애 중 API 성공률 3.0~15.1%, 요청 1건 200/2.05초/ZSet 유실
- 현재 소비자의 카운트 쿼리는 이벤트마다 풀/인덱스 전체 스캔 (EXPLAIN)
- ZSet 읽기는 승격 판정과 목록 화면뿐, hot ZSet 은 정렬에 쓰는 곳 없음
- RabbitMQ 유휴 129.7MiB, 부하 후 135.6MiB, app/mysql/redis 최대 사용량
- 3.4절의 이벤트 선택·삭제 쿼리와 3.7절의 조건부 UPDATE 가 MySQL 8.0 에서 문법·동작 모두 유효함 (스크래치 DB 검증: `maxId` 이후에 들어온 이벤트는
  삭제 뒤에도 남고, 조건부 UPDATE 는 임계값 3 으로 낮추면 한 문장으로 1,324행 갱신, 롤백 확인). 성능은 미측정

**추정한 것**
- 스레드 고갈이 Redis 장애 중 API 정지의 원인 (스레드 덤프 미확인)
- 합치기의 Redis 쓰기 감소량 (균일 분포에서는 거의 없음), 36만 행의 디스크 크기, 폴러의 추가 메모리
- RabbitMQ 워터마크·컨테이너 상한의 적정값 (150~160MiB / 192m)

**확인하지 못한 것**
- 소비 지연, 폴링 쿼리 수, 재구성 완료 시간, 운영 크기 데이터에서의 실행 계획
- 목록 조회 API 가 Redis 장애 때 실제로 느려지는지 (코드상으로만 확인)
- caddy 의 실제 메모리 사용량, 인스턴스 OS/Docker 데몬이 쓰는 메모리 (1GB 여유 68MB 가 실제로 안전한지)
- kill -9 드리프트가 유실 이벤트 때문인지 재기동 직후 경합 때문인지 (Phase 1에서 구분 못 함)

## 10. 결정이 필요한 것

1. **옵션 Z(ZSet 제거)와 안 1(ZSet 유지 + Outbox 폴러) 중 어느 쪽으로 갈지.** (A) 와 (C) 를 지키면 ZSet 을 읽는 코드가 없어진다는 0절 1번 때문이다.
2. 안 1을 택한다면: 폴러 wake-up 주기(즉시 + 안전망 5초 제안), `event_outbox` 행 삭제 vs 이력 보존.
3. RabbitMQ 안(안 2)은 소비자가 늘기 전까지 보류하고, 인스턴스 업그레이드 시점에 다시 볼지.
