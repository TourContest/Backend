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
| app | 메모리 560m. JVM 옵션은 `Dockerfile` 의 `JAVA_OPTS` 에서 `-Xmx` 만 320m→256m 으로 줄인 튜닝값 (`-Xmx256m -Xss512k -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxDirectMemorySize=32m`), Tomcat `max-connections` 300 / `accept-count` 50 (아래 「튜닝」 참고). 운영 값을 그대로 쓰려면 `.env` 에 `APP_JAVA_OPTS` |
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
python3 loadtest/run.py probe     # 트래픽 없이 리마인더 개선 전/후 쿼리 수 (유저 수는 DB 기준)
python3 loadtest/run.py drain     # 트래픽 없이 리마인더 outbox 7,000건 적체 해소 시간 (POLL_THREADS=2 로 폴러 동시 실행 → 중복 선점 검증)
python3 loadtest/run.py idem      # 3분 혼합 부하(같은 requestId 재전송 10%) 후 원장 감사만
loadtest/tune.sh <라벨> "<JAVA_OPTS>" <tomcat스레드> <hikari풀>   # app 만 다른 설정으로 재기동해 350~400 TPS 소크
loadtest/jvm-mem.sh               # JVM 영역별 메모리
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

## 함정 (측정이 조용히 틀어지는 경우)

- **`DELETE FROM shedlock` 금지.** `JdbcTemplateLockProvider` 가 락 행 존재를 캐시해서, 지운 뒤에는 락을 영영 못 얻고 리마인더가 조용히 스킵된다(컨트롤러는 "성공"을 응답). 락 해제는 `lock_until` 을 `UTC_TIMESTAMP()` 기준 과거로 돌리는 UPDATE 로 한다 (`run.py` 의 `release_lock`).
- 리마인더 dedup 키는 `attendance:{date}` 라 같은 날 두 번째 실행은 outbox 에 안 들어간다. 반복 측정 전에는 outbox 를 비운다 (`run.py` 가 처리).
- STEP 알림은 dedup 키가 `step-goal:{date}` 라 유저당 하루 1건이다. outbox 부하는 dedup 키가 달라지는 `/reply` 로 만든다.

## 튜닝 (350~400 TPS 에서 OOMKilled 방지)

컨테이너 메모리는 힙보다 비힙이 더 크다. 앱은 기동만 해도 약 505MiB(한도 560MiB)를 쓴다.
힙 committed ≈ 250 + 메타스페이스·클래스 스페이스 ≈ 145(상한 160, 더 못 줄임) + 코드캐시 25 + 스레드 스택 등.

| 실험 | 설정 | 결과 (350→400 TPS 각 90초) |
|---|---|---|
| E0 | 원본(-Xmx320m, 연결 제한 없음) | 350 TPS 구간에서 에러 19.6%, 그 뒤 **OOMKilled** |
| E1 | -Xmx256m | **OOMKilled** (힙이 원인이 아님) |
| E2 | -Xmx256m + max-connections 300 + accept-count 50 | **생존**, 메모리 최대 503MiB, 재시작 0 |
| E3 | E2 + Tomcat 스레드 100 + Hikari 10 | 생존, 메모리 최대 516MiB, 처리량 개선 없음 → 채택 안 함 |

과부하일 때 k6 연결이 쌓이면 커널 소켓/slab 메모리가 30MB 이상 늘어 560MiB 를 넘었다(cgroup `sock` 12KB→26MB, `kernel` 3.5→16MB, `slab` 1→13MB).
연결 수를 제한하면 넘치는 요청은 에러로 빨리 떨어지고 프로세스는 산다. 이 튜닝은 "죽지 않는 것"이지 처리량을 늘리는 것이 아니다 — 한계는 CPU 다.

운영 반영 시 필요한 것(이 저장소의 운영 설정은 바꾸지 않았다): `server.tomcat.max-connections`(현재 8192)와 `accept-count`, 그리고 `JAVA_OPTS` 의 `-Xmx`.

## 기준선 (2026-09-20, 로컬 Docker Desktop, 위 튜닝 설정)

측정 환경에 따라 달라지므로 다음 작업과의 비교용 기준값이다.

**mixed (50 TPS, 유저 10,000명, 리마인더 대상 7,000건)**

| 구간 | 요청 | p95 | p99 | 에러 |
|---|---|---|---|---|
| 전 (트리거 전) | 5,274 | 16.0ms | 98.6ms | 0% |
| 중 (트리거~배수 완료, 14초) | 683 | 17.4ms | 165.4ms | 0% |
| 후 (배수 완료 후 181초) | 9,027 | 12.8ms | 16.4ms | 0% |

- 리마인더 트리거 451ms. outbox 7,000건 해소 **13.7초** (수정 전 732초).
- 원장 감사(재전송 907건 포함): 원장 합계 = 잔액 합계 = 85,640, `exchange_count` 합계 = 원장 행 수 = 2,814, `converted_points` 합계 = 원장 합계, 잔액 불일치 0명, 중복 멱등 키 0건, 한도-원장 불일치 유저 0명.
- app 메모리 505~515MiB, 재시작 0.

**리마인더 적체 해소 (트래픽 없음, 7,000건)**: 수정 전 720.7초 → 수정 후 8.8~12.5초 (3회). 폴러 2스레드를 추가로 동시에 돌린 5회(동시 호출 최대 2~4) 모두 스텁이 받은 메시지 7,000 = DB SENT 7,000, **중복 선점 0건**.

**리마인더 쿼리 수 (generate_statistics)**: 유저 1만 명 개선 전 20,001회 / 2만 명 40,001회 (= 2N+1) → 개선 후 1회. `User.receivedMoodGrades` 가 `@ElementCollection(fetch = EAGER)`(`User.java:122`)라 `findAll()` 이 유저마다 컬렉션 쿼리를 추가로 낸다.

**limit (50 TPS 씩 30초)**

| 목표 | 도달 | 에러 | p95 | dropped |
|---|---|---|---|---|
| 200 | 200 | 0% | 85ms | 0 |
| 250 | 250 | 0% | 61ms | 0 |
| 300 | 262 | 1.7% | 1.6초 | 478 |
| 350 | 301 | 26.8% | 10초 | 752 |
| 600 | 565 | 79.2% | 10초 | 1,260 |

한계 TPS ≈ **250** (에러 1% 초과 첫 지점 300). 600 TPS 까지 앱은 죽지 않았고(재시작 0, OOM 없음) app 메모리 최대 524MiB, CPU 평균 135% / 최대 178% (2코어 공유 한도 200%).
