#!/usr/bin/env bash
set -euo pipefail

docker network inspect middleware_net >/dev/null 2>&1 || docker network create middleware_net
docker volume create postgres_data >/dev/null
docker volume create redis_data >/dev/null
docker volume create mysql_data >/dev/null
docker volume create xxl_job_logs >/dev/null

docker rm -f postgres redis mysql xxl-job-admin rmqbroker rmqnamesrv rmqdashboard >/dev/null 2>&1 || true

docker run -d --name postgres --restart unless-stopped \
  --network middleware_net \
  -e POSTGRES_USER=root \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=postgres \
  -v postgres_data:/var/lib/postgresql/data \
  -p 10.10.10.1:5432:5432 \
  pgvector/pgvector:pg16

docker run -d --name redis --restart unless-stopped \
  --network middleware_net \
  -v redis_data:/data \
  -p 10.10.10.1:6379:6379 \
  redis:7 redis-server --appendonly yes --requirepass 123456

docker run -d --name mysql --restart unless-stopped \
  --network middleware_net \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -e MYSQL_DATABASE=xxl_job \
  -v mysql_data:/var/lib/mysql \
  -p 10.10.10.1:3306:3306 \
  mysql:8.0 --default-authentication-plugin=mysql_native_password

docker run -d --name rmqnamesrv --restart unless-stopped \
  --network middleware_net \
  -p 10.10.10.1:9876:9876 \
  apache/rocketmq:5.3.1 sh mqnamesrv

docker run -d --name rmqbroker --restart unless-stopped \
  --network middleware_net \
  -e NAMESRV_ADDR=rmqnamesrv:9876 \
  -p 10.10.10.1:10911:10911 \
  -p 10.10.10.1:10909:10909 \
  apache/rocketmq:5.3.1 sh mqbroker -n rmqnamesrv:9876 --enable-proxy

# Wait NameServer ready before starting dashboard (8 * 4s)
ready=0
for i in $(seq 1 8); do
  if docker logs --tail 200 rmqnamesrv 2>&1 | grep -q "The Name Server boot success"; then
    ready=1
    break
  fi
  if docker exec rmqbroker sh -lc "command -v nc >/dev/null 2>&1 && nc -z rmqnamesrv 9876" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 4
done

if [ "$ready" -ne 1 ]; then
  echo "rocketmq namesrv not ready after retries" >&2
  exit 1
fi

docker run -d --name rmqdashboard --restart unless-stopped \
  --network middleware_net \
  -e JAVA_OPTS="-Drocketmq.namesrv.addr=rmqnamesrv:9876" \
  -p 10.10.10.1:8081:8082 \
  apacherocketmq/rocketmq-dashboard:latest
