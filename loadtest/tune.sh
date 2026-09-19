#!/bin/bash
# app 컨테이너만 주어진 설정으로 다시 만들고 350~400 TPS 를 버티는지 본다 (DB/Redis 는 유지).
# 사용: loadtest/tune.sh <라벨> "<JAVA_OPTS>" <TOMCAT_MAX_THREADS> <HIKARI_POOL_SIZE>
#       (연결 제한은 TOMCAT_MAX_CONNECTIONS / TOMCAT_ACCEPT_COUNT 환경변수)
set -uo pipefail
cd "$(dirname "$0")/.."
LABEL=$1; export APP_JAVA_OPTS=$2 TOMCAT_MAX_THREADS=${3:-50} HIKARI_POOL_SIZE=${4:-5}
echo "### $LABEL | JAVA_OPTS=$APP_JAVA_OPTS | tomcat=$TOMCAT_MAX_THREADS hikari=$HIKARI_POOL_SIZE"
docker compose -f loadtest/docker-compose.yml --env-file loadtest/.env up -d --no-deps --force-recreate app >/dev/null 2>&1
for _ in $(seq 1 60); do curl -s localhost:8081/actuator/health 2>/dev/null | grep -q '"db":{"status":"UP"}' && break; sleep 2; done
START=${START:-350} MAX_TPS=${MAX_TPS:-400} STEP=50 HOLD=${HOLD:-90} SAMPLE_S=5 python3 loadtest/run.py limit | tail -8
echo "--- JVM 메모리 (종료 시점) ---"; loadtest/jvm-mem.sh 2>&1 | head -4
