# 하루제주 부하테스트 (k6)

운영과 같은 자원 제한 위에서 **걸음수 · 알림 · 12시 출석 리마인더**를 함께 돌려 보는 로컬 부하테스트 세트.

## 구성

| 파일 | 역할 |
|---|---|
| `docker-compose.yml` | app / mysql / redis 스택. 운영 제한 적용 (아래 표) |
| `up.sh` | `.env`(JWT 시크릿) 생성 → 스택을 새로 띄움(DB·Redis 초기화) → 유저 10,000명 시드 |
| `seed.sql` | `lt{1..N}@loadtest.local` 유저 시드 (FCM 토큰 포함, 알림 수신 ON) |
| `jejuday.js` | k6 시나리오 `mixed`, `limit` |
| `run.py` | 실행/분석 드라이버: 리마인더 수동 실행, outbox 배수 시간, 구간별 p95, 원장 감사, 한계 TPS 계산 |

FCM 은 `loadtest` 프로파일의 `StubFcmGateway`(`src/main/.../notification/loadtest/`)가 대신한다.
`sendEach` 1회당 200ms(`LOADTEST_FCM_LATENCY_MS`)만 지연시키고 전부 성공 처리한다. 서비스 계정 JSON 이 필요 없다.
운영 프로파일에는 영향이 없다 (`FirebaseConfig` 는 `@Profile("!loadtest")`).

## 운영 제한 재현

| 대상 | 제한 |
|---|---|
| app | 메모리 560m, JVM 옵션은 `Dockerfile` 의 `JAVA_OPTS` 그대로 (`-Xmx320m -Xss512k -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxDirectMemorySize=32m`) |
| mysql | 메모리 220m. 맞추려고 `performance_schema=OFF`, `innodb_buffer_pool_size=64M`, `max_connections=60` 로 튜닝 |
| redis | 메모리 80m, `maxmemory 64mb` |
| CPU | 세 컨테이너가 같은 2코어(`cpuset: "0,1"`)를 공유 |

가정: "CPU 2개"는 호스트 전체(EC2 2 vCPU)로 해석해 세 컨테이너에 같은 코어 2개를 공유시켰다. 컨테이너별 2코어가 의도라면 `cpuset` 을 서비스별로 나누면 된다.
운영의 실제 mysql/redis 설정은 저장소에 없어서 위 튜닝값은 추정이다.

## 실행

```bash
loadtest/up.sh                    # 스택 기동 + 시드 (앱: http://localhost:8081)
python3 loadtest/run.py mixed     # 50 TPS 혼합 + 2분 지점 리마인더 (약 20분)
python3 loadtest/run.py limit     # 50→600 TPS 단계 상승, 한계 TPS 탐색 (약 8분)
```

필요: Docker, k6, Python 3.11+. 결과는 `loadtest/results/<mode>-<시각>/` 에 쌓이며 git 에서 무시된다.
`limit` 이후 앱이 죽어 있을 수 있으니 다음 실행 전에 `up.sh` 를 다시 돌릴 것.

### mixed

- `constant-arrival-rate` 50 TPS: 걸음수 30/s + 알림 20/s (iteration 1회 = API 1회).
- 걸음수: 업로드 70% / 포인트 전환 30%. **전체 걸음수 요청의 10%는 직전 전환 요청을 같은 `requestId` 로 재전송**한다.
  `POST /v1/steps` 에는 멱등 키가 없어서(설계상 캡으로 수용) 멱등 키 재전송은 `POST /v1/steps/convert` 에 적용했다.
- 알림: 발송 10% / 목록 60% / 안읽은 수 30%. 발송은 dedup 키가 매번 다른 `/v1/test-notification/reply` 를 쓴다.
  STEP 알림은 dedup 키가 `step-goal:{date}` 라 유저당 하루 1건만 outbox 에 들어가 부하가 되지 않는다.
- 측정 시작(t0) 후 120초에 `POST /v1/test-notification/attendance` 를 실행한다 (대상 10,000명 중 3,000명 출석 처리 → outbox 7,000건).
- 구간: 전(15초 워밍업 제외 ~ 트리거) / 중(트리거 ~ ATTENDANCE outbox 전부 SENT) / 후(배수 완료 후 180초).
- 시작 전, 트래픽 없는 상태에서 `attendance/before`·`attendance/after` 를 3회씩 교차 실행해 Hibernate 쿼리 수를 남긴다 (`generate_statistics=true`).
- 종료 후 SQL 로 원장 합계 vs 잔액 불일치, 중복 멱등 키, 쿼터 소진 vs 원장 차이를 센다.

### limit

- `ramping-arrival-rate`, 50 TPS 씩 올리며 단계당 30초 유지 (램프 5초는 분석 제외).
- 에러는 5xx 와 타임아웃만 센다 (4xx 는 한도/걸음수 부족 같은 비즈니스 거절).
- 결과: 단계별 도달 TPS · 에러율 · p95/p99 · dropped_iterations, `last_ok_tps`, `first_tps_over_1pct_error`.
- 앱이 죽은 뒤 단계(연결 거부 100%)는 성능 측정이 아니므로 `containers` 의 `oom` 여부와 같이 읽어야 한다.

## 참고 결과 (2026-09-20, 로컬 Docker Desktop)

측정 환경에 따라 달라지므로 수치는 참고용이다.

- mixed 50 TPS: 전 p95 15.6ms / 중 14.5ms / 후 15.3ms, 에러 0%. 리마인더 트리거 425ms.
- ATTENDANCE 7,000건이 전부 SENT 되기까지 732초. `OutboxPoller` 가 10초마다 최대 100건(`findPendingBatch` LIMIT 100)만 처리하기 때문에 처리량이 약 10건/s 로 고정된다.
- 리마인더 쿼리 수: 개선 전 약 20,001회(3.5초) → 개선 후 1회(50ms).
- limit: 250 TPS 까지 정상(p95 89ms), 300 TPS 에서 p95 3.6초 + dropped 발생, 350 TPS 구간에서 app 컨테이너 OOMKilled.
