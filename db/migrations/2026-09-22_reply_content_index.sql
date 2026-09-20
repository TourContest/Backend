-- reply (content_id, depth) 인덱스
--   승격 판정과 목록 화면이 스팟의 삭제되지 않은 최상위 댓글 수를 content_id 로 센다.
--   인덱스가 없으면 풀 테이블 스캔이다 (EXPLAIN type=ALL, 측정: 댓글 5,975행에서 rows≈6,088).
--
-- 적용 시점: 배포 전 수동 적용을 권장한다. (엔티티에 같은 이름/컬럼으로 선언돼 있어 ddl-auto=update 가 기동 시 만들기도 하지만,
--   큰 테이블에서 기동 중 인덱스 생성이 오래 걸리지 않게 미리 만들어 두는 편이 안전하다. 이미 있으면 Hibernate 는 건너뛴다.)
-- 한 번만 실행한다 (재실행 시 Duplicate key name 오류).

CREATE INDEX idx_reply_content_depth ON reply (content_id, depth);

-- 롤백: DROP INDEX idx_reply_content_depth ON reply;
