# 하루제주

> 걷기 리워드와 미션형 관광지 추천을 결합한 제주 여행 플랫폼

<img width="2600" height="1300" alt="image" src="https://github.com/user-attachments/assets/952e3083-26e9-48a2-b94b-ae0f62a156a8" />

2025 관광 데이터 활용 공모전 출품작 · 2025.05 ~ 2025.09
프론트엔드 2, 백엔드 2, 디자이너 1 (5인)

배포 URL : https://jejuday.duckdns.org

스토어 URL : https://m.onestore.co.kr/v2/ko-kr/app/0001008289

---

## 목차

- [서비스 소개](#서비스-소개)
- [기술 스택](#기술-스택)
- [아키텍처](#아키텍처)
- [ERD](#erd)
- [API 명세](#api-명세)
- [개발 내용](#개발-내용)
- [실행 방법](#실행-방법)
- [프로젝트 구조](#프로젝트-구조)
- [테스트](#테스트)
- [기술적 의사결정](#기술적-의사결정)

---

## 서비스 소개

제주 관광객은 도민 인구의 20배를 넘어섰지만 방문은 소수의 유명 관광지에 집중되어, 성수기 쓰레기 발생량이 평소 대비 30% 이상 증가합니다. 한편 한국인의 하루 평균 걸음수는 6천 보로 WHO 권장 기준의 60% 수준에 그칩니다. 걷기의 보상을 흩어진 장소에서만 얻도록 설계하면 두 문제를 하나의 행동으로 연결할 수 있다고 보고 서비스 기획을 시작했습니다.

**핵심 기능**

| 기능 | 설명 |
|---|---|
| 걸음수 리워드 | 걸음수를 한라봉 포인트로 환산, 누적 걸음수 기반 5단계 등급 |
| 위치 기반 미션 | 개인화 추천 장소에서 GPS·사진으로 방문 인증 |
| UGC 커뮤니티 | 방문 후기가 반응에 따라 관광 명소, 다시 챌린지 장소로 자동 승격 |
| 리워드 상점 | 모은 포인트로 제주 굿즈 교환 (공항 수령) |
| AI 개인화 추천 | 선호 테마와 장소 설명을 임베딩으로 매칭, 혼잡한 곳은 후순위로 밀어 방문 분산 |

운영진이 콘텐츠를 공급하지 않아도 사용자 기록이 다음 사용자의 추천 대상이 되는 구조를 목표로 설계했습니다.

---

## 기술 스택

**Backend**

| 분류 | 사용 기술 |
|---|---|
| Language / Framework | Java 21, Spring Boot 3.5 |
| Persistence | Spring Data JPA, Hibernate, MySQL 8.0 |
| Cache | Redis 7 |
| Security | Spring Security, OAuth2, JWT |
| Test | JUnit5, Mockito, Testcontainers |
| Monitoring & Logging | Sentry, Grafana Alloy, Grafana Cloud (Prometheus remote_write) |
| Docs | Swagger (springdoc-openapi) |

**Infra**

AWS EC2 · RDS for MySQL · S3 · Route 53 / Nginx · Docker · GitHub Actions
-> AWS Lightsail · S3 / Docker · Docker Compose / Caddy / Grafana Alloy / GitHub Actions
- 초기에는 EC2 + RDS 위에 Nginx와 Let's Encrypt로 HTTPS를 직접 구성했으나, 프리티어 종료 이후 상시 가동 비용을 고정하기 위해 현재 구성으로 이전했습니다.

**External**

Firebase Cloud Messaging · Kakao Login API · 한국관광공사 TourAPI · 비짓제주 OpenAPI · OpenAI API · 카카오맵 API · Gmail SMTP

**Frontend**

TypeScript · React · Capacitor

**Collaboration**

Jira · Slack · Git · Swagger

---

## 아키텍처

<img width="1024" height="559" alt="image" src="https://github.com/user-attachments/assets/f5c5156d-bc71-448c-b31c-4b84b8df2597" />

단일 EC2 인스턴스 위에서 Nginx와 Spring Boot, Redis를 컨테이너로 운영하고, 데이터는 RDS(MySQL), 이미지는 S3에 저장합니다. `main` 브랜치에 푸시하면 GitHub Actions가 빌드와 테스트를 수행하고 Docker Hub에 이미지를 올린 뒤, EC2가 이를 받아 재기동합니다.
-> 24시간 상시 가동이 전제라 종량제 EC2 대신 고정 월정액인 Lightsail을 택했고, 관리형 DB 비용을 줄이기 위해 MySQL·Redis를 인스턴스 안에서 직접 운영합니다.

---

## ERD

<img width="1017" height="596" alt="스크린샷 2025-06-05 오후 10 29 09" src="https://github.com/user-attachments/assets/ec0a8a89-1769-45ba-9091-22bb575db47e" />

---

## API 명세

<table>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/db6b3982-df79-4e4d-8d9c-19beb01e0e3f" width="100%"></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/d0b72ca5-a487-410d-a25b-252762423633" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/6558329c-dd92-48ff-b59e-60b88edbfb84" width="100%"></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/331972a8-2218-4bf0-be8b-826e5e81199c" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/520d57a1-ef7c-4448-8456-f25a95ada9fb" width="100%"></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/c4201fc3-f49f-4a28-9f1a-cd290e889f23" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/6505ddec-22f9-4c9c-bfdd-98f570c0384b" width="100%"></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/c9229e40-5525-4e2b-a096-a616931764e1" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/da9b1d9e-7ceb-4eae-b487-c77802456b09" width="100%"></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/099a2174-3f60-47fb-9878-baa8dfd8807a" width="100%"></td>
  </tr>
</table>

## 개발 내용

### 인증 · 회원

- 카카오 OAuth와 일반 로그인을 하나의 인증 플로우로 통합 — 소셜 로그인 사용자는 비밀번호가 없어 표준 방식으로 처리되지 않아, 가입 타입별로 SecurityContext를 다르게 구성
- 이메일 인증 기반 회원가입 — SMS 대신 이메일을 택해 건당 인증 비용 제거
- 인증을 완료하지 않은 임시 회원이 계속 쌓이는 문제를 정리 스케줄러로 해소
- 컨트롤러마다 반복되던 닉네임 검증 조건문을 `@ValidNickname` 커스텀 어노테이션으로 분리

### 걸음수 · 등급

- 걸음수를 한라봉 포인트로 환산하고, 일일 교환 한도로 비정상 적립 차단
- 누적 걸음수 기반 5단계 등급과 달성 시 자동 보상 지급
- 전날 걸음수를 다음 날 시작 보너스로 환산 — 하루 단위로 끊기던 참여를 다음 날로 잇기 위한 설계

### 출석

- 연속 출석 일수에 따른 보상과 7일 보너스
- `(user_id, check_date)` 유니크 제약으로 중복 출석을 DB 레벨에서 차단하고, 처리 성공 직후 캐시를 갱신해 조회 경로와 동기화

### 상점

- 포인트 기반 상품 교환과 등급별 구매 제한
- 상품 정보는 변경 빈도가 낮고 조회는 잦아 Spring Cache로 캐싱, 교환 발생 시 `@CacheEvict`로 갱신
- 구매 내역 조회에서 발생하던 N+1을 Fetch Join으로 해소

### 알림

- 7가지 알림 타입과 사용자별 알림 수신 설정
- 알림 적재를 비즈니스 트랜잭션에 포함하고 전송은 Outbox 폴러로 분리 — 상세는 [기술적 의사결정](#기술적-의사결정) 참고
- `(user_id, type, dedup_key)` 유니크 제약으로 크론 재실행 시 중복 발송 차단

### 관광지 · 승격

- 커뮤니티 게시글이 반응에 따라 관광 명소로, 명소 중 상위권이 챌린지 장소로 승격되는 3단계 큐레이션 파이프라인
- 승격 판정용 점수와 피드 정렬용 점수를 분리해 각각 별도 Redis ZSet으로 관리
- 좋아요·댓글 변경 시점의 이벤트로 점수를 증분 갱신하고, 매일 새벽 보정 배치가 DB 실측값과 대조

### 추천(고도화 담당)

- 기준 스팟 근처에서 **SPOT(공공데이터) 3곳 + CHALLENGE(UGC 승격) 1곳**을 반환 — `GET /api/spots/{spotId}/nearby-recommendations`
- 두 타입을 한 풀에 섞어 상위 4개를 뽑으면 개수가 압도적인 SPOT이 전부 차지해 CHALLENGE가 노출되지 않아, 후보 풀과 스코어링을 타입별로 분리
- 점수는 `테마 유사도 - 0.3 × 거리 - 0.4 × 혼잡도` — 오버투어리즘 분산이 서비스 목적이라 거리보다 혼잡도에 더 큰 페널티를 부여
- 혼잡도 데이터가 없는 스팟은 페널티 0 — 모르는 값을 "붐빈다"로 단정하지 않기 위한 처리
- 후보가 부족하면 반경을 단계적으로 확장 (SPOT 2→5→10km, CHALLENGE 10→100km) — CHALLENGE는 표본이 적어 처음부터 넓게 탐색
- 항상 1등만 노출하면 같은 장소가 반복되므로 최종 선정에 **점수 가중 랜덤(softmax, T=0.3)** 적용 — 고득점 편향은 유지하면서 결과에 다양성 확보
- 상위 후보 30곳 중 12곳을 가중 랜덤으로 추려 GPT에 재정렬 요청, 응답은 `{"rankedSpotIds": [...]}` JSON만 수신
- GPT 호출 실패·타임아웃 시 예외를 밖으로 던지지 않고 점수 가중 랜덤 결과로 폴백 — 외부 API 장애가 추천 기능 자체를 막지 않도록 설계
- 추천 메서드는 트랜잭션으로 묶지 않음 — 외부 호출이 포함된 구간을 트랜잭션에 넣으면 응답을 기다리는 동안 DB 커넥션을 점유하기 때문 (필요한 값은 모두 즉시 로딩)

### 임베딩

- 스팟은 `이름 + 카테고리 + 개요(500자)`를 조합해 임베딩 생성, 벡터는 JSON으로 저장하고 원문 SHA-256 해시를 함께 기록해 재계산 대상 판별에 사용
- 유저 테마 임베딩은 최초 생성 시 1회 계산해 캐싱 — 테마는 소수의 값이 재사용되므로 매 요청마다 계산할 이유가 없음
- 테마 임베딩 계산이 실패해도 회원가입은 진행 — 추천 품질 저하는 배치 재계산으로 복구 가능하지만 가입 실패는 복구 불가
- 배치 전체를 하나의 트랜잭션으로 묶지 않음 — 한 건이 JPA 예외를 내면 세션이 rollback-only로 마킹돼 개별 try/catch로도 마지막에 전체가 터지기 때문

### 관광지 데이터 동기화

- TourAPI 변경분 동기화 → 상세정보 보강 → 임베딩 생성 → 혼잡도 재계산을 하나의 파이프라인으로 통합, 매일 새벽 3시 실행
- 각 단계가 앞 단계의 결과에 의존하므로(임베딩 품질은 개요 유무에 좌우) 순서를 고정
- 최초 전체 적재는 일회성 작업이라 파이프라인에서 제외 — 매일 전량을 다시 수집하면 낭비
- 상세정보는 스팟당 외부 API 3회 호출이라 항목별로 트랜잭션을 분리하고, 응답에 섞여 오는 HTML 태그는 Jsoup으로 제거
- 관리자 수동 트리거(`POST /api/admin/spots/sync`)와 스케줄러가 같은 파이프라인 본체를 호출 — 배포 직후 검증 경로와 운영 경로를 일치

### 혼잡도

- 실시간 혼잡도 공공 API가 없어 자체 데이터로 근사 — 최근 24시간 조회 로그와 방문 인증 건수를 집계 후 최댓값 기준 0~1로 정규화
- 방문 인증은 조회보다 실제 방문을 나타내는 강한 신호라 3배 가중
- 매시 정각 재계산, ShedLock으로 다중 인스턴스 중복 실행 차단
- 계산 로직을 소비 측(추천)과 분리해, 향후 공공 방문자 데이터를 붙일 때 이 클래스만 교체하면 되도록 구성

### 챌린지 인증

- 방문 인증 사진 업로드를 별도 엔드포인트로 분리(`POST /api/challenges/{id}/proof-image`) — 반환된 URL을 완료 요청에 실어 보내는 2단계 구조
- 완료 처리는 위치 근접성 검사와 인증 사진을 모두 통과해야 포인트 지급, 멱등 키 `userId:CHALLENGE:challengeId`로 챌린지당 1회 보장
- TourAPI로 동기화된 SPOT은 보상 포인트가 비어 있는 경우가 많아 기본 지급액으로 폴백
- HEIC/HEIF는 대부분의 브라우저·안드로이드에서 렌더링되지 않아 업로드 시점에 차단, 검증 로직은 공통 유틸로 분리해 스팟 등록과 공유

### 공통 규격

- 도메인마다 제각각이던 응답 형태를 `ApiResponse` 래퍼로 통일하고, 도메인 예외 10종을 `GlobalExceptionHandler` 한 곳에서 상태 코드로 변환
- 게시글 등록에 이미지와 JSON 본문이 함께 필요했으나 기본 설정으로는 멀티파트 안의 JSON이 역직렬화되지 않아 `MultipartJackson2HttpMessageConverter` 등록
- 요청 본문 파싱 실패(JSON 형식 오류, enum 미정의 값)와 DB 제약조건 위반을 각각 400으로 변환 — 서비스 계층이 못 걸러낸 경우에도 500이 나가지 않도록 방어
- 존재하지 않는 정적 리소스(robots.txt, favicon) 요청은 404로 처리하고 Sentry 수집 대상에서 제외 — 장애가 아닌 요청이 오류 알림을 채우는 문제 해소
- 비로그인 상태로 인증 필요 API를 호출하면 principal이 null로 들어와 500이 발생하던 문제를 컨트롤러 단 방어로 400 처리
- 프론트 연동용으로 컨트롤러 전반에 `@Tag`·`@Operation` 설명을 추가해 Swagger 문서만으로 요청 순서(사진 업로드 → 완료)를 파악할 수 있도록 보완


### 운영 시 고려한 점 (1GB 인스턴스 운영 최적화)

- 4개 컨테이너 동시 기동 시 메모리 고갈로 SSH까지 불능이 되는 문제를 겪은 뒤, 스왑 2GB 구성과 순차 기동 스크립트(MySQL 헬스체크 대기 → Redis → App → Caddy)로 피크 부하 제거
- 컨테이너별 `mem_limit` 지정으로 특정 컨테이너의 메모리 점유가 전체로 번지지 않게 격리
- MySQL은 performance schema·바이너리 로그 비활성화와 버퍼 풀 축소로 메모리 사용 40% 절감, Redis는 캐시 전용이라 스냅샷·AOF를 끔
- JVM은 힙 상한과 Metaspace를 명시해 컨테이너 상한 안에서만 동작하도록 제한
- 적용 결과 스왑 사용량 697Mi → 267Mi

### 운영 모니터링

- Sentry로 운영 환경 Exception을 실시간 수집 — 발생 즉시 스택 트레이스와 요청 컨텍스트 확인
- 메트릭은 Grafana Cloud로 원격 전송 — 1GB 단일 인스턴스에 Prometheus 서버를 직접 올리면 애플리케이션과 메모리를 경합하므로, 서버에는 경량 에이전트만 두는 구성
- API 오류율 · 응답 시간 · JVM Heap · CPU/Memory 사용량을 대시보드로 시각화해 장애 원인 분석과 운영 상태 확인에 활용

---

## 실행 방법

### 요구 사항

- JDK 21
- MySQL 8.0
- Redis 7

### 환경 변수

프로젝트 루트에 `.env` 파일을 만들고 아래 값을 채웁니다.

```env
DB_URL=jdbc:mysql://localhost:3306/jejuday
DB_USERNAME=
DB_PASSWORD=

REDIS_HOST=localhost
REDIS_PORT=6379

JWT_SECRET=

KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=
KAKAO_REDIRECT_URI=

AWS_ACCESS_KEY=
AWS_SECRET_KEY=
AWS_S3_BUCKET=

FCM_CONFIG_PATH=

TOUR_API_KEY=

VISITJEJU_API_KEY=

OPENAI_API_KEY=

SYNC_TOKEN=

MAIL_USERNAME=
MAIL_PASSWORD=
```

### 로컬 실행

```bash
./gradlew clean build
./gradlew bootRun
```

### Docker 실행

```bash
docker build -t jejuday-backend .
docker run -p 8080:8080 --env-file .env jejuday-backend
```

---

## 프로젝트 구조

```
src/main/java/com/goodda/jejuday
├── auth              인증 · 회원 · 프로필 · 유저 테마
├── steps             걸음수 · 등급 · 포인트 환전
├── attendance        출석 체크 · 연속 보상
├── spot              관광지 · 검색 · 챌린지
│   ├── ranking       승격 판정 · 랭킹 갱신 · 혼잡도 계산 · 보정 배치
│   ├── search        Trie 기반 자동완성
│   ├── service       추천 스코어링 · 동기화 파이프라인
│   └── tourapi       TourAPI 클라이언트 · 스팟/상세 동기화
├── openai            임베딩 생성 배치 · GPT 재정렬
├── pay               포인트 원장 · 상점 · 교환
├── notification      알림 · Outbox 폴러 · FCM 게이트웨이
├── crawler           축제 정보 수집
└── common            공통 응답 · 예외 · 설정 · AOP
```

---

## 테스트

통합 테스트는 Testcontainers 기반으로 실제 MySQL·Redis 컨테이너를 띄워 실행합니다. 동시성은 `CountDownLatch` 기반 병렬 요청으로, 장애 상황은 프로세스 강제 종료와 이벤트 인위 주입으로 재현했습니다.

```bash
./gradlew test
```

> Docker가 실행 중이어야 합니다.

---

## 기술적 의사결정

알림 유실, 포인트 중복 적립, 승격 판정 결함 등 이 프로젝트에서 마주한 문제를 어떤 판단으로 해결했는지 아래에 정리했습니다.

**➡️ [하루제주 포트폴리오](https://galvanized-binder-e76.notion.site/77cdf3e0b4458204af6f81cce2d0de72?pvs=74)**

| 다룬 문제 | 결과 |
|---|---|
| 외부 장애가 사용자 요청에 전이되지 않는 알림 구조 | 강제 종료 후 재기동 시 유실 0건 |
| 재시도해도 결과가 같은 포인트 변동 구조 | 100회 병렬 재시도에 원장 1건, 잔액 불일치 0건 |
| 목적이 다른 두 점수를 분리한 승격 판정 | 동시 판정 2스레드에 전이 1회, 알림 1건 |
| 캐시가 새 병목이 되지 않는 리마인더 조회 | 조회 20,003회 → 1회, 처리 시간 워밍업 후 3회 중앙값 기준 약 30배 단축 |
| 확인과 차감 사이의 시간차를 제거한 재고 처리 | 재고1+동시10 → 1건 / 재고100+동시1,000 → 초과판매 0건 |
| 총량이 아니라 피크를 줄인 1GB 인스턴스 메모리 설계 | 스왑 사용 697 → 267Mi, MySQL 195 → 118MiB |

## 서비스 화면

---

<table>
  <tr>
    <td align="center"><b>스팟 추가</b><br><img src="https://github.com/user-attachments/assets/256de8d3-702f-490c-b4fe-15551cbf8d05" width="100%"></td>
    <td align="center"><b>커뮤니티</b><br><img src="https://github.com/user-attachments/assets/af252fb6-2a76-4430-882f-d0a7c5fdba77" width="100%"></td>
  </tr>
  <tr>
    <td align="center"><b>챌린지</b><br><img src="https://github.com/user-attachments/assets/a8ae1e08-1acf-44f4-aed0-4434ab40254c" width="100%"></td>
    <td align="center"><b>한라봉으로 구매하기</b><br><img src="https://github.com/user-attachments/assets/e788997b-6aa6-4505-91e1-e37bd1959954" width="100%"></td>
  </tr>
</table>

