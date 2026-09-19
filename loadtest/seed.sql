-- 부하테스트용 유저 시드 (N명, 이미 있는 유저는 건너뛴다). 사용: (echo 'SET @n=20000;'; cat seed.sql) | mysql ...
SET SESSION cte_max_recursion_depth = 200000;
SET @n = IFNULL(@n, 10000);
INSERT INTO users (email, nickname, platform, gender, birth_year, language, created_at,
                   is_kakao_login, is_notification_enabled, hallabong, total_steps,
                   received_signup_bonus, total_referrals, fcm_token)
WITH RECURSIVE seq(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < @n)
SELECT CONCAT('lt', i, '@loadtest.local'), CONCAT('lt', i), 'APP', 'MALE', '1995', 'KOREAN', NOW(),
       0, 1, 0, 0, 1, 0, CONCAT('fake-fcm-token-loadtest-', LPAD(i, 8, '0'))
FROM seq
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.email = CONCAT('lt', i, '@loadtest.local'));
