-- notification_entity: 멱등 알림(걸음수 목표, 승격) 선택적 유니크
--   nullable dedup_key 컬럼 + UNIQUE (user_id, type, dedup_key). NULL 은 유니크에서 제외되므로
--   댓글/답글/댓글 좋아요처럼 같은 context_key 가 의도적으로 반복되는 알림은 영향을 받지 않는다.
--
-- 적용 시점: **배포 전**에 수동 적용 (운영은 ddl-auto=update).
--   - 앱이 먼저 뜨면 Hibernate 가 컬럼/인덱스를 만들지만, 기존 중복 행 정리와 dedup_key 백필은 하지 않는다.
--   - 아래를 먼저 적용하면 앱 기동 시 이미 존재하는 컬럼/인덱스를 그대로 쓴다 (이름이 같아야 한다:
--     컬럼 dedup_key VARCHAR(255), 인덱스 uk_notification_dedup).
-- 이 스크립트는 한 번만 실행한다 (ADD COLUMN 이 두 번째에는 오류: MySQL 8 에는 IF NOT EXISTS 가 없다).

-- 1) 컬럼 추가 (기존 행은 NULL)
ALTER TABLE notification_entity ADD COLUMN dedup_key VARCHAR(255) NULL;

-- 2) 중복 현황 확인 — 멱등 대상 키만 (그 외 키의 중복은 정상 동작이므로 건드리지 않는다)
SELECT user_id, type, context_key, COUNT(*) AS cnt
FROM notification_entity
WHERE context_key LIKE 'step-goal:%' OR context_key LIKE 'spot-promote:%' OR context_key LIKE 'challenge-promote:%'
GROUP BY user_id, type, context_key
HAVING COUNT(*) > 1;

-- 3) 멱등 대상 키의 중복 행 삭제: 가장 오래된 행(MIN(id))만 남긴다
DELETE n
FROM notification_entity n
JOIN (
    SELECT MIN(id) AS keep_id, user_id, type, context_key
    FROM notification_entity
    WHERE context_key LIKE 'step-goal:%' OR context_key LIKE 'spot-promote:%' OR context_key LIKE 'challenge-promote:%'
    GROUP BY user_id, type, context_key
    HAVING COUNT(*) > 1
) d ON d.user_id = n.user_id AND d.type = n.type AND d.context_key = n.context_key
WHERE n.id <> d.keep_id;

-- 4) 기존 행 백필 — 배포 후 같은 알림이 한 번 더 쌓이는 것을 막는다
UPDATE notification_entity
SET dedup_key = context_key
WHERE dedup_key IS NULL
  AND (context_key LIKE 'step-goal:%' OR context_key LIKE 'spot-promote:%' OR context_key LIKE 'challenge-promote:%');

-- 5) 유니크 인덱스
ALTER TABLE notification_entity
    ADD CONSTRAINT uk_notification_dedup UNIQUE (user_id, type, dedup_key);

-- 롤백:
--   ALTER TABLE notification_entity DROP INDEX uk_notification_dedup;
--   ALTER TABLE notification_entity DROP COLUMN dedup_key;
