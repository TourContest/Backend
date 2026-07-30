# 하루제주

> 걷기 리워드와 미션형 관광지 추천을 결합한 제주 여행 플랫폼

<img width="2600" height="1300" alt="image" src="https://github.com/user-attachments/assets/952e3083-26e9-48a2-b94b-ae0f62a156a8" />

2025 관광 데이터 활용 공모전 출품작 · 2025.05 ~ 2025.09
프론트엔드 2, 백엔드 2, 디자이너 1 (5인)

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
| Monitoring & Logging | Sentry, Grafana, Prometheus |
| Docs | Swagger (springdoc-openapi) |

**Infra**

AWS EC2 · RDS for MySQL · S3 · Route 53 / Nginx · Docker · GitHub Actions
-> AWS Lightsail · S3 / Docker · Docker Compose / Caddy / GitHub Actions
- 초기에는 EC2 + RDS 위에 Nginx와 Let's Encrypt로 HTTPS를 직접 구성했으나, 프리티어 종료 이후 상시 가동 비용을 고정하기 위해 현재 구성으로 이전했습니다.

**External**

Firebase Cloud Messaging · Kakao Login API · 한국관광공사 TourAPI · 카카오맵 API · Gmail SMTP

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

### 공통 규격

- 도메인마다 제각각이던 응답 형태를 `ApiResponse` 래퍼로 통일하고, 도메인 예외 10종을 `GlobalExceptionHandler` 한 곳에서 상태 코드로 변환
- 게시글 등록에 이미지와 JSON 본문이 함께 필요했으나 기본 설정으로는 멀티파트 안의 JSON이 역직렬화되지 않아 `MultipartJackson2HttpMessageConverter` 등록


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
├── auth              인증 · 회원 · 프로필
├── steps             걸음수 · 등급 · 포인트 환전
├── attendance        출석 체크 · 연속 보상
├── spot              관광지 · 검색 · 챌린지
│   ├── ranking       승격 판정 · 랭킹 갱신 · 보정 배치
│   └── search        Trie 기반 자동완성
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
| 캐시가 새 병목이 되지 않는 리마인더 조회 | 조회 20,003회 → 1회, 처리 시간 97% 단축 |
| 확인과 차감 사이의 시간차를 제거한 재고 처리 | 동시 1,000건에 정확히 100건만 차감 |

## 서비스 화면

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

