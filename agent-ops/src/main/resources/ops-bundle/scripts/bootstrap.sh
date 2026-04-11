#!/usr/bin/env bash
set -euo pipefail

POSTGRES_IMAGE="${POSTGRES_IMAGE:-pgvector/pgvector:pg16}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7.2}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
KAFKA_IMAGE="${KAFKA_IMAGE:-confluentinc/cp-kafka:7.6.1}"
KAFKA_UI_IMAGE="${KAFKA_UI_IMAGE:-provectuslabs/kafka-ui:v0.7.2}"
ROCKETMQ_IMAGE="${ROCKETMQ_IMAGE:-apache/rocketmq:5.3.1}"
ROCKETMQ_DASHBOARD_IMAGE="${ROCKETMQ_DASHBOARD_IMAGE:-apacherocketmq/rocketmq-dashboard:latest}"
ROCKETMQ_BROKER_IP="${ROCKETMQ_BROKER_IP:-10.10.10.1}"
ROCKETMQ_BROKER_CONF="${ROCKETMQ_BROKER_CONF:-/opt/agent-ops/scripts/broker.conf}"
REDIS_RESET_DATA="${REDIS_RESET_DATA:-true}"
KAFKA_RESET_DATA="${KAFKA_RESET_DATA:-true}"
KAFKA_CLUSTER_ID="${KAFKA_CLUSTER_ID:-q1Sh-9_ISia_zwGINzP0Lw}"

docker network inspect middleware_net >/dev/null 2>&1 || docker network create middleware_net
docker volume create postgres_data >/dev/null
if [ "${REDIS_RESET_DATA}" = "true" ]; then
  docker volume rm -f redis_data >/dev/null 2>&1 || true
fi
docker volume create redis_data >/dev/null
docker volume create mysql_data >/dev/null
docker volume create xxl_job_logs >/dev/null

if [ "${KAFKA_RESET_DATA}" = "true" ]; then
  docker volume rm -f kafka_kraft_data >/dev/null 2>&1 || true
fi
docker volume create kafka_kraft_data >/dev/null

docker rm -f postgres redis mysql xxl-job-admin kafka kafka-ui rmqnamesrv rmqbroker rmqdashboard >/dev/null 2>&1 || true

docker run -d --name postgres --restart unless-stopped \
  --network middleware_net \
  -e POSTGRES_USER=root \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=postgres \
  -v postgres_data:/var/lib/postgresql/data \
  -p 10.10.10.1:5432:5432 \
  "${POSTGRES_IMAGE}"

docker run -d --name redis --restart unless-stopped \
  --network middleware_net \
  -v redis_data:/data \
  -p 10.10.10.1:6379:6379 \
  "${REDIS_IMAGE}" redis-server --appendonly yes --requirepass 123456

docker run -d --name mysql --restart unless-stopped \
  --network middleware_net \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -e MYSQL_DATABASE=xxl_job \
  -v mysql_data:/var/lib/mysql \
  -p 10.10.10.1:3306:3306 \
  "${MYSQL_IMAGE}" --default-authentication-plugin=mysql_native_password

docker run -d --name kafka --restart unless-stopped \
  --network middleware_net \
  -e CLUSTER_ID="${KAFKA_CLUSTER_ID}" \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:29093 \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,PLAINTEXT_INTERNAL://0.0.0.0:29092,CONTROLLER://0.0.0.0:29093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://10.10.10.1:9092,PLAINTEXT_INTERNAL://kafka:29092 \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT_INTERNAL \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_MIN_INSYNC_REPLICAS=1 \
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
  -v kafka_kraft_data:/var/lib/kafka/data \
  -p 10.10.10.1:9092:9092 \
  "${KAFKA_IMAGE}"

docker run -d --name kafka-ui --restart unless-stopped \
  --network middleware_net \
  -e KAFKA_CLUSTERS_0_NAME=local \
  -e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=kafka:29092 \
  -p 10.10.10.1:8083:8080 \
  "${KAFKA_UI_IMAGE}"

docker run -d --name rmqnamesrv --restart unless-stopped \
  --network middleware_net \
  -p 10.10.10.1:9876:9876 \
  "${ROCKETMQ_IMAGE}" \
  sh mqnamesrv

# Wait RocketMQ NameServer ready (8 * 4s)
rmq_ns_ready=0
for i in $(seq 1 8); do
  if docker logs rmqnamesrv 2>&1 | grep -qi "The Name Server boot success"; then
    rmq_ns_ready=1
    break
  fi
  sleep 4
done

if [ "$rmq_ns_ready" -ne 1 ]; then
  docker logs --tail 200 rmqnamesrv || true
  echo "rocketmq namesrv not ready after retries" >&2
  exit 1
fi

if [ -f "${ROCKETMQ_BROKER_CONF}" ]; then
  docker run -d --name rmqbroker --restart unless-stopped \
    --network middleware_net \
    -e NAMESRV_ADDR=rmqnamesrv:9876 \
    -p 10.10.10.1:10911:10911 \
    -p 10.10.10.1:10909:10909 \
    -v "${ROCKETMQ_BROKER_CONF}:/home/rocketmq/rocketmq-5.3.1/conf/broker.conf:ro" \
    "${ROCKETMQ_IMAGE}" \
    sh /home/rocketmq/rocketmq-5.3.1/bin/mqbroker -n rmqnamesrv:9876 -c /home/rocketmq/rocketmq-5.3.1/conf/broker.conf --enable-proxy
else
  docker run -d --name rmqbroker --restart unless-stopped \
    --network middleware_net \
    -e NAMESRV_ADDR=rmqnamesrv:9876 \
    -p 10.10.10.1:10911:10911 \
    -p 10.10.10.1:10909:10909 \
    "${ROCKETMQ_IMAGE}" \
    sh -c "cat > /home/rocketmq/rocketmq-5.3.1/conf/broker.conf <<EOF
brokerClusterName=DefaultCluster
brokerName=broker-a
brokerId=0
deleteWhen=04
fileReservedTime=48
brokerRole=ASYNC_MASTER
flushDiskType=ASYNC_FLUSH
brokerIP1=${ROCKETMQ_BROKER_IP}
EOF
/home/rocketmq/rocketmq-5.3.1/bin/mqbroker -n rmqnamesrv:9876 -c /home/rocketmq/rocketmq-5.3.1/conf/broker.conf --enable-proxy"
fi

docker run -d --name rmqdashboard --restart unless-stopped \
  --network middleware_net \
  -e NAMESRV_ADDR=rmqnamesrv:9876 \
  -p 10.10.10.1:8081:8082 \
  "${ROCKETMQ_DASHBOARD_IMAGE}"

# Wait Kafka ready (8 * 4s)
kafka_ready=0
for i in $(seq 1 8); do
  if docker exec kafka bash -lc "kafka-topics --bootstrap-server kafka:29092 --list >/dev/null 2>&1"; then
    kafka_ready=1
    break
  fi
  sleep 4
done

if [ "$kafka_ready" -ne 1 ]; then
  docker logs --tail 200 kafka || true
  echo "kafka not ready after retries" >&2
  exit 1
fi

LOG_TOPIC="${LOG_TOPIC:-agent.log.events}"
docker exec kafka bash -lc "kafka-topics --bootstrap-server kafka:29092 --create --if-not-exists --topic ${LOG_TOPIC} --partitions 3 --replication-factor 1 >/dev/null 2>&1 || true"
