-- 승격 경로 재현용 스팟 시드 (POST 타입, 작성자는 유저 1..500). 사용: (echo 'SET @n=3000;'; cat seed-spots.sql) | mysql ...
-- id 1..N 이 연속이라는 가정을 k6(engagement)가 쓴다 → 빈 spot 테이블에서만 실행할 것.
SET SESSION cte_max_recursion_depth = 200000;
SET @n = IFNULL(@n, 3000);
INSERT INTO spot (type, is_deleted, latitude, longitude, like_count, view_count, user_id, name, created_at, is_user_created)
WITH RECURSIVE seq(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < @n)
SELECT 'POST', 0, 33.4 + (i % 100) / 1000, 126.5 + (i % 100) / 1000, 0, 0, 1 + (i % 500), CONCAT('lt-spot-', i), NOW(6), 1
FROM seq
WHERE (SELECT COUNT(*) FROM spot) = 0;
