#!/bin/bash
# 운영 제한(app 560m / mysql 220m / redis 80m, 2 CPU)으로 부하테스트 스택을 새로 띄운다.
# 매번 DB/Redis 를 초기화하고 유저 10,000명을 시드한다.
set -euo pipefail
cd "$(dirname "$0")"

# k6 와 서버가 같은 JWT 시크릿을 쓰도록 .env 에 한 번만 생성 (커밋 금지)
if [ ! -f .env ]; then
  echo "JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')" > .env
fi

docker compose --env-file .env down -v --remove-orphans >/dev/null 2>&1 || true
docker compose --env-file .env up -d --build

echo "앱 기동 대기..."
for _ in $(seq 1 90); do
  curl -s localhost:8081/actuator/health 2>/dev/null | grep -q '"db":{"status":"UP"}' && break
  sleep 2
done

(echo "SET @n=${USERS:-10000};"; cat seed.sql) | docker exec -i jejuday-lt-mysql-1 mysql -uroot -ploadtest jejuday_loadtest 2>&1 | grep -v Warning || true
echo "준비 완료: http://localhost:8081  (JWT_SECRET 은 loadtest/.env)"
