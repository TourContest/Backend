-- 부하테스트용 유저 시드 (N명). 사용: mysql ... -e "set @n=10000; source seed.sql"
SET SESSION cte_max_recursion_depth = 200000;
SET @n = IFNULL(@n, 10000);
INSERT INTO users (email, nickname, platform, gender, birth_year, language, created_at,
                   is_kakao_login, is_notification_enabled, hallabong, total_steps,
                   received_signup_bonus, total_referrals, fcm_token)
WITH RECURSIVE seq(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < @n)
SELECT CONCAT('lt', i, '@loadtest.local'), CONCAT('lt', i), 'APP', 'MALE', '1995', 'KOREAN', NOW(),
       0, 1, 0, 0, 1, 0, CONCAT('fake-fcm-token-loadtest-', LPAD(i, 8, '0'))
FROM seq
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'lt1@loadtest.local');
