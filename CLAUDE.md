# CLAUDE.md — Jejuday Backend

## 프로젝트 개요
Spring Boot 3.5.0 / Java 21 백엔드. MySQL 8 + Redis 7 + Firebase FCM 사용.

## 빌드 & 실행

```bash
# 로컬 (Docker)
docker compose up -d

# 빌드만
./gradlew clean build -x test

# 테스트 포함 빌드
./gradlew clean build

# 특정 테스트 실행
./gradlew test --tests "com.goodda.jejuday.notification.*"
```

## 환경변수
`.env.example`을 복사해 `.env`로 만든 후 값 채우기.
```bash
cp .env.example .env
```

## 아키텍처 원칙
- **SOLID**: SRP(커맨드/쿼리 서비스 분리), DIP(인터페이스 추상화), OCP(전략 패턴)
- **Outbox 패턴**: FCM 알림 유실 방지 — `notification_outbox` 테이블로 재시도 보장
- **CQRS-lite**: `NotificationCommandService`(쓰기) / `NotificationQueryService`(읽기) 분리
- **Redis SCAN**: `KEYS` 대신 `scanKeys()` 사용 (블로킹 방지)

## 패키지 구조
```
src/main/java/com/goodda/jejuday/
├── attendance/          # 출석체크, 할라봉 보상
├── auth/                # 로그인, 회원가입, Kakao OAuth
├── common/              # 글로벌 예외처리, 공통 DTO
├── notification/
│   ├── config/          # @EnableRetry, 스케줄러 설정
│   ├── controller/      # REST API
│   ├── dto/             # NotificationCommand, NotificationDto
│   ├── entity/          # NotificationEntity, NotificationOutbox
│   ├── exception/       # NotificationException 계층
│   ├── port/            # PushNotificationSender (DIP 인터페이스)
│   ├── repository/
│   ├── service/         # 커맨드/쿼리/어드민/스케줄러
│   └── util/            # NotificationConstants (상수 집중 관리)
└── ...
```

## 코드 리뷰 체크리스트
PR을 리뷰할 때 아래 항목을 확인한다.

### 필수
- [ ] 컨트롤러에 try-catch 없음 (GlobalExceptionHandler에 위임)
- [ ] Redis `KEYS` 대신 `cacheManager.scanKeys()` 사용
- [ ] FCM 토큰 검증 시 `NotificationConstants.isValidFcmToken()` 사용
- [ ] 매직 스트링 없음 — `NotificationConstants`에 상수로 정의
- [ ] `@AuthenticationPrincipal CustomUserDetails` 일관 사용
- [ ] 새 알림 타입 추가 시 `NotificationPort` 인터페이스에 메서드 추가
- [ ] DB 조회 루프 없음 — 벌크 쿼리 또는 `IN` 절 사용

### 보안
- [ ] SQL 인젝션 — `@Query`에 파라미터 바인딩 사용 (`:param`)
- [ ] 민감 정보 로그 출력 없음 (FCM 토큰은 `maskFcmToken()` 사용)
- [ ] `.env`, `firebase/*.json` 커밋 없음

### 성능
- [ ] `N+1` 쿼리 없음 (`@EntityGraph` 또는 fetch join 사용)
- [ ] 페이지네이션 — `List<T>` 반환 대신 `Page<T>` 사용
- [ ] `@Transactional(readOnly = true)` — 조회 전용 메서드에 적용

## PR 가이드라인
```
제목: [타입] 간결한 설명 (50자 이내)
타입: FEAT | FIX | REFACTOR | CHORE | DOCS | TEST

본문:
- 무엇을 왜 변경했는지 설명
- 관련 이슈 번호 (closes #123)
```

## 테스트 보일러플레이트

### 서비스 유닛 테스트
```java
@ExtendWith(MockitoExtension.class)
class NotificationCommandServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationOutboxRepository outboxRepository;
    @Mock NotificationValidator validator;
    @Mock NotificationCacheManager cacheManager;
    @Mock PushNotificationSender pushSender;

    @InjectMocks NotificationCommandService sut;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@test.com")
                .fcmToken("valid-fcm-token-longer-than-twenty-chars")
                .isNotificationEnabled(true)
                .build();
    }

    @Test
    void 알림_전송_차단되면_저장하지_않는다() {
        // given
        NotificationCommand command = NotificationCommand.builder()
                .user(user).message("test").type(NotificationType.ATTENDANCE)
                .contextKey("attendance:2024-01-01").token(user.getFcmToken())
                .build();
        given(validator.isNotificationAllowed(any(), any(), any())).willReturn(false);

        // when
        sut.send(command);

        // then
        then(notificationRepository).should(never()).save(any());
    }
}
```

### 컨트롤러 슬라이스 테스트
```java
@WebMvcTest(NotificationController.class)
@Import(SecurityTestConfig.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean NotificationService notificationService;

    @Test
    @WithMockCustomUser
    void 알림_목록_조회_200() throws Exception {
        given(notificationService.getNotifications(any(), any()))
                .willReturn(Page.empty());

        mockMvc.perform(get("/api/notifications")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
```

### 레포지토리 테스트
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class NotificationOutboxRepositoryTest {

    @Autowired NotificationOutboxRepository repository;

    @Test
    void PENDING_상태만_조회된다() {
        // given
        repository.save(NotificationOutbox.builder()
                .userId(1L).message("test").type(NotificationType.ATTENDANCE)
                .contextKey("key").targetToken("token")
                .build());

        // when
        List<NotificationOutbox> result = repository.findPendingEntries(
                LocalDateTime.now(), PageRequest.of(0, 10));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
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

## 주요 상수 위치
모든 매직 스트링 / 상수는 `NotificationConstants`에서 관리:
- FCM 기본 제목: `FCM_DEFAULT_TITLE`
- 컨텍스트 키 빌더: `challengeContextKey()`, `replyContextKey()` 등
- 캐시 정리 패턴: `CACHE_CLEAR_PATTERNS`
- 토큰 검증: `isValidFcmToken()`, `maskFcmToken()`

## 시크릿 설정 (GitHub Actions)
| Secret | 설명 |
|--------|------|
| `DOCKERHUB_USERNAME` | Docker Hub 아이디 |
| `DOCKERHUB_TOKEN` | Docker Hub Access Token |
| `DEPLOY_HOST` | 배포 서버 IP |
| `DEPLOY_USER` | 배포 서버 SSH 유저 |
| `DEPLOY_SSH_KEY` | 배포 서버 SSH 개인키 |
