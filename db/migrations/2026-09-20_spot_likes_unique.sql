-- spot_likes (user_id, target_type, target_id) 유니크 제약 추가
--
-- 수동 적용용, 배포 전에 실행 (순서는 README.md). 이 저장소에는 마이그레이션 도구가 없고 운영은 ddl-auto=update 다.
-- ddl-auto=update 는 엔티티의 @UniqueConstraint 를 만들려 하지만, 기존 데이터에 중복이 있으면 실패하고
-- 조용히 넘어간다(제약 없이 기동). 그래서 운영에서는 아래 순서로 먼저 정리한다.
-- 주의: 키가 (user_id, spot_id) 가 아니다. spot_likes 는 스팟 좋아요(target_type=SPOT)와
--       댓글 좋아요(target_type=REPLY, spot_id=댓글이 달린 스팟)를 함께 담는다.

-- 1) 중복 현황 확인
SELECT user_id, target_type, target_id, COUNT(*) AS cnt
FROM spot_likes
GROUP BY user_id, target_type, target_id
HAVING COUNT(*) > 1;

-- 2) 가장 오래된 행(MIN(id))만 남기고 삭제
DELETE l
FROM spot_likes l
JOIN (
    SELECT MIN(id) AS keep_id, user_id, target_type, target_id
    FROM spot_likes
    GROUP BY user_id, target_type, target_id
    HAVING COUNT(*) > 1
) d ON d.user_id = l.user_id AND d.target_type = l.target_type AND d.target_id = l.target_id
WHERE l.id <> d.keep_id;

-- 3) (선택) 중복 때문에 부풀었을 수 있는 spot.like_count 를 실제 행 수로 재계산
-- UPDATE spot s
-- LEFT JOIN (SELECT target_id, COUNT(*) AS c FROM spot_likes WHERE target_type = 'SPOT' GROUP BY target_id) l
--        ON l.target_id = s.id
-- SET s.like_count = COALESCE(l.c, 0);

-- 4) 유니크 제약
ALTER TABLE spot_likes
    ADD CONSTRAINT uk_spot_likes_user_target UNIQUE (user_id, target_type, target_id);
