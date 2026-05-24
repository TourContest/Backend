# CLAUDE.md — Jejuday Backend

## 프로젝트 개요
Spring Boot 3.5.0 / Java 21 백엔드. MySQL 8 + Redis 7 + Firebase FCM.

---

## 🔒 보안 — 절대 규칙

**채팅에 시크릿을 공유하지 말 것.** 아래 형태는 즉시 차단된다:
- GitHub PAT (`ghp_`, `github_pat_`)
- GitLab 토큰 (`glpat-`)
- Slack 토큰 (`xoxb-`, `xoxp-`)
- OpenAI API 키 (`sk-`)
- 기타 API 키 / 비밀번호 / JWT 시크릿

시크릿이 필요하면 `.env` 파일이나 환경변수를 사용하라.  
실수로 노출된 토큰은 **즉시 폐기(revoke)** 후 재발급해야 한다.

---

## 빌드 & 실행

```bash
# 로컬 전체 스택 (MySQL + Redis + App)
docker compose up -d

# 빌드 (테스트 제외)
./gradlew clean build -x test

# 테스트 포함 빌드
./gradlew clean build

# 모듈별 테스트
./gradlew test --tests "com.goodda.jejuday.notification.*"
./gradlew test --tests "com.goodda.jejuday.auth.*"
./gradlew test --tests "com.goodda.jejuday.attendance.*"
```

환경변수 설정:
```bash
cp .env.example .env   # 값 채우기
```

---

## 패키지 구조

```
src/main/java/com/goodda/jejuday/
├── attendance/      # 출석체크, 할라봉 보상
├── auth/            # 로그인, 회원가입, Kakao OAuth, JWT
├── common/          # GlobalExceptionHandler, 공통 DTO, AOP
├── crawler/         # 제주 이벤트 크롤러
├── notification/
│   ├── config/      # @EnableRetry, Redis, Firebase 설정
│   ├── controller/  # REST API
│   ├── dto/         # NotificationCommand, NotificationDto
│   ├── entity/      # NotificationEntity, NotificationOutbox
│   ├── exception/   # 예외 계층
│   ├── port/        # PushNotificationSender (DIP 인터페이스)
│   ├── repository/
│   ├── service/     # Command / Query / Admin / Scheduler
│   └── util/        # NotificationConstants (상수 집중 관리)
├── pay/             # 상품, 결제, 교환
├── spot/            # 스팟, 커뮤니티, 챌린지, 검색
└── steps/           # 만보기, 포인트 전환
```

---

## 아키텍처 원칙

| 원칙 | 적용 방식 |
|------|-----------|
| SRP | Command/Query 서비스 분리 (`NotificationCommandService` / `NotificationQueryService`) |
| OCP | 전략 패턴 — `PushNotificationSender` 인터페이스로 FCM 교체 가능 |
| DIP | `NotificationPort` 인터페이스 — 외부 서비스는 구현체 직접 참조 금지 |
| Outbox 패턴 | FCM 유실 방지 — `notification_outbox` 테이블로 재시도 보장 |
| CQRS-lite | 읽기/쓰기 서비스 물리적 분리 |
| Redis SCAN | `KEYS` 금지 — `cacheManager.scanKeys()` 사용 (블로킹 방지) |

---

## PR 자동화 — Claude 행동 규칙

PR이 생성되거나 리뷰 요청이 들어오면:

1. **변경 범위 파악**: 어떤 레이어(컨트롤러/서비스/레포지토리)가 변경됐는지 확인
2. **아래 체크리스트 실행**: 모든 항목을 순서대로 점검
3. **인라인 코멘트**: 문제 발견 시 해당 줄에 구체적인 수정 제안
4. **승인/수정 요청**: 모든 필수 항목 통과 시 Approve, 아니면 Request Changes

### 코드 리뷰 체크리스트

**아키텍처**
- [ ] 컨트롤러에 비즈니스 로직 없음 (서비스에 위임)
- [ ] 컨트롤러에 try-catch 없음 (GlobalExceptionHandler에 위임)
- [ ] 새 알림 타입 → `NotificationPort` 인터페이스에 메서드 추가 여부

**코드 품질**
- [ ] 매직 스트링 없음 — 상수는 해당 모듈의 Constants 클래스에
- [ ] Redis `KEYS` 대신 `cacheManager.scanKeys()` 사용
- [ ] FCM 토큰 검증 시 `NotificationConstants.isValidFcmToken()` 사용
- [ ] `@AuthenticationPrincipal CustomUserDetails` 일관 사용

**보안**
- [ ] `@Query`에 `:param` 바인딩 사용 (SQL 인젝션 방지)
- [ ] 민감 정보 로그 없음 (FCM 토큰은 `maskFcmToken()` 사용)
- [ ] `.env`, `firebase/*.json` 커밋 없음

**성능**
- [ ] N+1 쿼리 없음 (`@EntityGraph` 또는 fetch join 사용)
- [ ] 페이지네이션 — `List<T>` 대신 `Page<T>` 반환
- [ ] 조회 전용 메서드에 `@Transactional(readOnly = true)` 적용
- [ ] DB 조회 루프 없음 — 벌크 쿼리 또는 `IN` 절 사용

---

## PR 작성 가이드

```
제목: [타입] 간결한 설명 (50자 이내)
타입: FEAT | FIX | REFACTOR | CHORE | DOCS | TEST

본문:
- 무엇을 왜 변경했는지 설명
- 관련 이슈 번호 (closes #123)
- 스크린샷 / API 변경점 (있는 경우)
```

---

## 테스트 보일러플레이트

### 서비스 유닛 테스트 (Mockito)

```java
@ExtendWith(MockitoExtension.class)
class SomeServiceTest {

    @Mock SomeRepository repository;
    @Mock SomeDependency dependency;
    @InjectMocks SomeService sut;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).email("test@test.com")
                .fcmToken("valid-fcm-token-longer-than-twenty-chars")
                .isNotificationEnabled(true)
                .build();
    }

    @Test
    void 조건이_충족되면_정상_처리된다() {
        // given
        given(repository.findById(1L)).willReturn(Optional.of(user));

        // when
        sut.doSomething(1L);

        // then
        then(dependency).should().callMethod(any());
    }

    @Test
    void 존재하지_않으면_예외가_발생한다() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.doSomething(99L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
```

### 컨트롤러 슬라이스 테스트 (WebMvcTest)

```java
@WebMvcTest(SomeController.class)
@Import(SecurityTestConfig.class)
class SomeControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean SomeService someService;

    @Test
    @WithMockCustomUser
    void GET_목록_조회_200() throws Exception {
        given(someService.getList(any(), any())).willReturn(Page.empty());

        mockMvc.perform(get("/api/some")
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockCustomUser
    void POST_생성_성공_201() throws Exception {
        var request = new SomeRequest("value");

        mockMvc.perform(post("/api/some")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        then(someService).should().create(any(), eq("value"));
    }
}
```

### 레포지토리 테스트 (DataJpaTest)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SomeRepositoryTest {

    @Autowired SomeRepository repository;
    @Autowired TestEntityManager em;

    @Test
    void 조건으로_조회된다() {
        // given
        em.persistAndFlush(SomeEntity.builder().status(Status.ACTIVE).build());
        em.clear();

        // when
        List<SomeEntity> result = repository.findByStatus(Status.ACTIVE);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(Status.ACTIVE);
    }
}
```

### 통합 테스트 베이스

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    @Autowired protected TestRestTemplate restTemplate;

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
```

---

## 주요 상수 위치

| 상수 | 위치 |
|------|------|
| FCM 기본 제목, 타임아웃 | `NotificationConstants.FCM_DEFAULT_TITLE`, `FCM_TIMEOUT_SECONDS` |
| 컨텍스트 키 빌더 | `NotificationConstants.challengeContextKey()` 등 |
| 캐시 정리 패턴 | `NotificationConstants.CACHE_CLEAR_PATTERNS` |
| 토큰 검증/마스킹 | `NotificationConstants.isValidFcmToken()`, `maskFcmToken()` |
| 할라봉 관련 | `HallabongConstants` |

---

## GitHub Actions 시크릿 설정

| Secret | 설명 |
|--------|------|
| `DOCKERHUB_USERNAME` | Docker Hub 아이디 |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token |
| `DEPLOY_HOST` | 배포 서버 IP |
| `DEPLOY_USER` | 배포 서버 SSH 유저 |
| `DEPLOY_SSH_KEY` | 배포 서버 SSH 개인키 |
