# Code Review Guidelines

## 심각도 정의

🔴 **Important** — 머지 전 반드시 수정해야 하는 버그:
- 잘못된 비즈니스 로직 (출석 보상 중복 지급, 포인트 계산 오류 등)
- SQL 인젝션 가능성 (`@Query`에 문자열 직접 삽입)
- 인증 없이 접근 가능한 API 엔드포인트
- PII(개인정보) 또는 FCM 토큰이 로그에 그대로 출력
- 하위 호환성을 깨는 DB 마이그레이션
- `notification_outbox` 상태 전환 버그 (PENDING → DEAD 누락 등)
- Redis `KEYS` 명령어 직접 사용 (프로덕션 블로킹 위험)
- `.env`, `firebase/*.json` 커밋

🟡 **Nit** — 스타일, 네이밍, 리팩토링 제안. 최대 **5개**만 보고.  
추가 Nit가 있으면 "외 N건 유사 항목" 요약으로 대체.

🟣 **Pre-existing** — PR이 도입한 게 아닌 기존 코드의 문제.

---

## 건너뛸 항목

다음은 리뷰하지 않는다:

- `build/`, `.gradle/`, `logs/` 하위 생성 파일
- `gradle/wrapper/gradle-wrapper.jar` 바이너리
- CI(GitHub Actions)가 이미 검사하는 컴파일 오류, 포맷 오류
- `*.log`, `*.gz` 로그 파일
- 의도적으로 규칙을 어기는 테스트 코드 (예: `@SuppressWarnings`)

---

## 반드시 확인할 항목

### 인증 & 보안
- 새 API 엔드포인트에 `@AuthenticationPrincipal CustomUserDetails` 적용 여부
- `@Query`가 `:param` 바인딩을 사용하는지 (문자열 직접 삽입 금지)
- 응답에 비밀번호, FCM 토큰, 개인정보 필드 포함 여부
- FCM 토큰 로깅 시 `maskFcmToken()` 사용 여부

### 데이터 정합성
- 트랜잭션 경계가 올바른지 (`@Transactional` 누락 또는 과다 적용)
- 출석/포인트/교환 로직에서 동시성 문제 가능성 (비관적/낙관적 락 필요 여부)
- `NotificationOutbox` 상태 전이 (`markProcessing → markDone / markFailed`) 완결성

### 성능
- 루프 안에서 DB 조회 (N+1)
- 페이지네이션 없이 전체 목록 반환
- Redis `KEYS` 대신 `scanKeys()` 사용

### 새 기능 추가 시
- 새 알림 타입 → `NotificationPort` 인터페이스에 메서드 있는지
- 새 엔드포인트 → 컨트롤러 슬라이스 테스트 있는지
- 새 스케줄러 → 멱등성 보장 여부 (중복 실행 시 안전한지)

---

## 재리뷰 정책

푸시 후 재리뷰 시:
- 이전 🔴 Important가 해결됐는지만 확인
- 새 Nit 추가 금지 (이미 지적된 항목 제외)
- 모든 Important 해결 시 "No blocking issues" 로 시작

---

## 자동 리뷰 트리거

PR 코멘트로 수동 실행:
```
@claude review        # 리뷰 후 이후 푸시도 자동 구독
@claude review once   # 1회만 리뷰 (구독 없음)
```

로컬 사전 리뷰 (머지 전 셀프 체크):
```bash
/code-review           # 현재 브랜치 diff 리뷰
/code-review --comment # 인라인 코멘트 포함
```
