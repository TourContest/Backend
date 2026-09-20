# JejudayApplicationTests.contextLoads 가 main 에서도 실패한다

## 증상
`./gradlew test --tests '*JejudayApplicationTests'` → `contextLoads()` FAILED. main 과 수정 브랜치 모두에서 같은 결과라
어떤 기능 변경과도 무관한 기존 문제다.

## 원인 (확인함)
`JejudayApplicationTests` 는 `@SpringBootTest` 만 붙어 있고 **프로필이 비어 있다** (`activeProfiles = []`).
그래서 `application.yml` 을 그대로 읽는데, 여기에는 기본값 없는 환경변수 플레이스홀더가 있다.

```
UnsatisfiedDependencyException: 'attendanceController' → 'attendanceService' → 'hallabongService' → 'userServiceImpl'
  → BeanCreationException: 's3Config': Injection of autowired dependencies failed
  → PlaceholderResolutionException: Could not resolve placeholder 'AWS_ACCESS_KEY_ID' in value "${AWS_ACCESS_KEY_ID}"
```

컨텍스트 생성은 첫 미해결 플레이스홀더에서 멈추므로 아래 값들도 순서대로 같은 문제를 일으킬 것이다 (확인은 첫 번째까지만):
`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_S3_BUCKET_NAME`, `AWS_REGION`, `JWT_SECRET`, `KAKAO_ID`, `KAKAO_SECRET`,
`KAKAO_REDIRECT_URL_SERVER`, `SPRING_MAIL_PASSWORD`, `TOUR_API_KEY`. 그 뒤에는 MySQL(localhost:3306)/Redis 연결과
`firebase/jejuday.json`(gitignore 대상)이 필요하다.

## 다른 통합 테스트는 왜 되는가
`@ActiveProfiles("it")`(`src/test/resources/application-it.yml`: Testcontainers MySQL, 더미 시크릿) +
`@MockitoBean` 으로 Firebase/Redis 를 대체한다. `JejudayApplicationTests` 만 이 구성이 빠져 있다.

## 제안 (이번에 수정하지 않음)
1. `@ActiveProfiles("it")` 와 다른 IT 와 같은 `@MockitoBean` 4종(FirebaseMessaging, RedisConnectionFactory, RedisTemplate, StringRedisTemplate)을 추가하거나,
2. 다른 IT 가 이미 컨텍스트 로딩을 검증하므로 이 테스트를 삭제한다.
