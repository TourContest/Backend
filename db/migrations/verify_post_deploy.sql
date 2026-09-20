-- 배포 후 확인 SQL (읽기 전용). 기대값은 각 쿼리 위 주석.
--
-- ddl-auto=update 는 유니크 인덱스를 만들다 기존 중복 때문에 실패하면 예외 없이 넘어간다(앱은 정상 기동).
-- 그래서 "앱이 떴다"로는 인덱스가 생겼다는 증거가 못 된다 — 반드시 아래로 확인한다.

-- 1) 유니크 인덱스 존재 — 기대: 2행, 둘 다 non_unique = 0
--    uk_spot_likes_user_target      : user_id,target_type,target_id
--    uk_notification_dedup          : user_id,type,dedup_key
SELECT table_name, index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN ('uk_spot_likes_user_target', 'uk_notification_dedup')
GROUP BY table_name, index_name, non_unique;

-- 2) dedup_key 컬럼 — 기대: 1행 (varchar(255), YES)
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'notification_entity' AND column_name = 'dedup_key';

-- 3) 남은 중복 — 기대: 모두 0
SELECT 'spot_likes 중복 키' AS check_name, COUNT(*) AS cnt FROM (
    SELECT 1 FROM spot_likes GROUP BY user_id, target_type, target_id HAVING COUNT(*) > 1) t
UNION ALL
SELECT 'notification_entity 멱등 알림 중복', COUNT(*) FROM (
    SELECT 1 FROM notification_entity WHERE dedup_key IS NOT NULL
    GROUP BY user_id, type, dedup_key HAVING COUNT(*) > 1) t
UNION ALL
SELECT '멱등 키인데 dedup_key 가 비어 있는 행 (백필 누락)', COUNT(*) FROM notification_entity
WHERE dedup_key IS NULL
  AND (context_key LIKE 'step-goal:%' OR context_key LIKE 'spot-promote:%' OR context_key LIKE 'challenge-promote:%');

-- 4) spot.like_count 와 실제 스팟 좋아요 행 수의 불일치 — 참고용 (중복 때문에 부풀었던 값이 남아 있으면 > 0)
--    0 이 아니면 2026-09-20_spot_likes_unique.sql 의 3단계(선택) 재계산을 검토한다.
SELECT COUNT(*) AS spots_like_count_mismatch
FROM spot s
LEFT JOIN (SELECT target_id, COUNT(*) AS c FROM spot_likes WHERE target_type = 'SPOT' GROUP BY target_id) l
       ON l.target_id = s.id
WHERE s.like_count <> COALESCE(l.c, 0);
