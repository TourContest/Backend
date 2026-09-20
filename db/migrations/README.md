# db/migrations

이 저장소에는 마이그레이션 도구(Flyway/Liquibase)가 없고 운영은 `ddl-auto=update` 다. 여기 SQL 은 **수동 적용용**이며
이 저장소의 코드는 운영 DB 에 아무 것도 적용하지 않는다.

`ddl-auto=update` 는 엔티티의 유니크 제약을 만들려다 기존 데이터에 중복이 있으면 **예외 없이 실패하고 앱은 정상 기동**한다.
그래서 중복 정리를 배포 전에 하고, 배포 후 인덱스가 실제로 생겼는지 확인해야 한다.

| 순서 | 파일 | 시점 |
|---|---|---|
| 1 | `2026-09-20_spot_likes_unique.sql` | 배포 **전** — 중복 좋아요 정리 + `uk_spot_likes_user_target` |
| 2 | `2026-09-21_notification_dedup_key.sql` | 배포 **전** — `dedup_key` 컬럼, 멱등 알림 중복 정리·백필, `uk_notification_dedup` |
| 3 | (앱 배포) | |
| 4 | `verify_post_deploy.sql` | 배포 **후** — 인덱스 2개 존재, 중복 0 확인 |

주의:
- `spot_likes` 유니크 키는 `(user_id, spot_id)` 가 아니라 `(user_id, target_type, target_id)` 다. 스팟 좋아요와 댓글 좋아요가 같은 테이블을 쓴다.
- `notification_entity` 유니크는 **멱등 알림(걸음수 목표 `step-goal:`, 승격 `spot-promote:`/`challenge-promote:`)에만** 적용된다.
  댓글/답글/댓글 좋아요 알림은 같은 `context_key` 가 반복되는 것이 정상이라 중복 정리 대상이 아니다.
- 각 스크립트는 한 번만 실행한다 (`ADD COLUMN`/`ADD CONSTRAINT` 는 재실행 시 오류).
- 실행 전 백업을 권장한다 (`DELETE` 포함).
