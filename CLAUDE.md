# CLAUDE.md — Jejuday Backend

## 프로젝트 개요
Spring Boot 3.5.0 / Java 21. MySQL 8 + Redis 7 + Firebase FCM.

---

## 🔒 보안 — 절대 규칙

채팅에 시크릿을 공유하지 말 것. 토큰/키가 필요하면 `.env` 파일을 사용하라.  
노출된 토큰은 즉시 폐기 후 재발급해야 한다.

절대 커밋하지 말 것:
- `.env`, `*.env`
- `src/main/resources/firebase/*.json`
- 어떤 형태의 API 키, JWT 시크릿, 비밀번호

---

## 빌드 & 실행

```bash
docker compose up -d          # 전체 스택 (MySQL + Redis + App)
./gradlew clean build -x test  # 빌드만
./gradlew clean build          # 테스트 포함
./gradlew test --tests "com.goodda.jejuday.notification.*"
```

```bash
cp .env.example .env  # 최초 환경변수 설정
```

---

## 코드 표준

### 컨트롤러
- 비즈니스 로직 없음 — 서비스에 위임
- try-catch 없음 — `GlobalExceptionHandler`가 처리
- 인증은 반드시 `@AuthenticationPrincipal CustomUserDetails` 사용
- `@CrossOrigin` 개별 사용 금지 — 전역 설정에서 관리

### 서비스
- 읽기 전용 메서드에 `@Transactional(readOnly = true)`
- 쓰기(Command)와 읽기(Query) 서비스 분리 유지
- DB 조회 루프 금지 — 벌크 쿼리 또는 `IN` 절 사용

### 레포지토리
- `@Query`는 반드시 `:param` 바인딩 (SQL 인젝션 방지)
- N+1 금지 — `@EntityGraph` 또는 fetch join 사용
- `List<T>` 반환 대신 `Page<T>` 사용 (페이지네이션 API)

### 알림 모듈
- Redis `KEYS` 금지 — `cacheManager.scanKeys()` 사용
- FCM 토큰 검증: `NotificationConstants.isValidFcmToken()`
- FCM 토큰 로깅: `NotificationConstants.maskFcmToken()`
- 매직 스트링 금지 — `NotificationConstants`에 상수로 정의
- 새 알림 타입 추가 시 `NotificationPort` 인터페이스에 메서드 추가

### 공통
- 매직 스트링 금지 — 모듈별 Constants 클래스에 정의
- 민감 정보(토큰, 비밀번호, 개인정보) 로그 출력 금지
- 빈 catch 블록 금지 — 최소한 로그 출력

---

## 아키텍처

| 패턴 | 적용 |
|------|------|
| CQRS-lite | `NotificationCommandService` / `NotificationQueryService` 분리 |
| Outbox 패턴 | FCM 유실 방지 — `notification_outbox` 테이블로 재시도 |
| DIP | `PushNotificationSender`, `NotificationPort` 인터페이스 추상화 |
| Redis SCAN | `KEYS` O(N) 블로킹 방지 |

---

## 테스트 표준

### 서비스 유닛 테스트
```java
@ExtendWith(MockitoExtension.class)
class FooServiceTest {
    @Mock FooRepository repository;
    @InjectMocks FooService sut;

    @Test
    void 조건이_충족되면_정상_처리된다() {
        given(repository.findById(1L)).willReturn(Optional.of(stub()));
        sut.doSomething(1L);
        then(repository).should().save(any());
    }

    @Test
    void 존재하지_않으면_예외가_발생한다() {
        given(repository.findById(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> sut.doSomething(99L))
                .isInstanceOf(NotFoundException.class);
    }
}
```

### 컨트롤러 슬라이스 테스트
```java
@WebMvcTest(FooController.class)
@Import(SecurityTestConfig.class)
class FooControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean FooService fooService;

    @Test
    @WithMockCustomUser
    void GET_목록_200() throws Exception {
        given(fooService.getList(any(), any())).willReturn(Page.empty());
        mockMvc.perform(get("/api/foo").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
```

### 레포지토리 테스트
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class FooRepositoryTest {
    @Autowired FooRepository repository;
    @Autowired TestEntityManager em;

    @Test
    void 상태로_조회된다() {
        em.persistAndFlush(FooEntity.builder().status(Status.ACTIVE).build());
        em.clear();
        assertThat(repository.findByStatus(Status.ACTIVE)).hasSize(1);
    }
}
```

---

## PR 작성 규칙

```
제목: [타입] 설명 (50자 이내)
타입: FEAT | FIX | REFACTOR | CHORE | DOCS | TEST

본문:
- 무엇을 왜 변경했는지
- closes #이슈번호
```

---

## 패키지 구조

```
src/main/java/com/goodda/jejuday/
├── attendance/   # 출석체크, 할라봉 보상
├── auth/         # 로그인, 회원가입, Kakao OAuth, JWT
├── common/       # GlobalExceptionHandler, AOP
├── crawler/      # 제주 이벤트 크롤러
├── notification/ # FCM 알림 (Outbox, CQRS, SCAN)
├── pay/          # 상품, 결제, 교환
├── spot/         # 스팟, 커뮤니티, 챌린지
└── steps/        # 만보기, 포인트 전환
```
