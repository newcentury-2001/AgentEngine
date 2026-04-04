# Docker Run 部署清单（仅 WireGuard 访问）

前提：
- WireGuard 服务端地址为 `10.10.10.1`
- 宿主机 `wg0` 已启动
- 安全组仅开放 `22/tcp`、`51820/udp`

## 0. 创建网络与数据卷
```bash
docker network create middleware_net

docker volume create postgres_data
docker volume create redis_data
docker volume create mysql_data
docker volume create xxl_job_logs
```

## 1. PostgreSQL（pgvector）
```bash
docker run -d \
  --name postgres \
  --restart unless-stopped \
  --network middleware_net \
  -e POSTGRES_USER=root \
  -e POSTGRES_PASSWORD='123456' \
  -e POSTGRES_DB=postgres \
  -v postgres_data:/var/lib/postgresql/data \
  -p 10.10.10.1:5432:5432 \
  pgvector/pgvector:pg16
```

## 2. Redis
```bash
docker run -d \
  --name redis \
  --restart unless-stopped \
  --network middleware_net \
  -v redis_data:/data \
  -p 10.10.10.1:6379:6379 \
  redis:7 \
  redis-server --appendonly yes --requirepass 123456
```

## 3. MySQL（给 XXL-Job 用）
```bash
docker run -d \
  --name mysql \
  --restart unless-stopped \
  --network middleware_net \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -e MYSQL_DATABASE=xxl_job \
  -e MYSQL_USER=root \
  -e MYSQL_PASSWORD=123456 \
  -v mysql_data:/var/lib/mysql \
  -p 10.10.10.1:3306:3306 \
  mysql:8.0 \
  --default-authentication-plugin=mysql_native_password
```

## 4. XXL-Job Admin（依赖 MySQL）
```bash
docker run -d \
  --name xxl-job-admin \
  --restart unless-stopped \
  --network middleware_net \
  -e PARAMS="--spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&serverTimezone=Asia/Shanghai \
  --spring.datasource.username=root \
  --spring.datasource.password=123456 \
  --xxl.job.accessToken=123456" \
  -v xxl_job_logs:/data/applogs \
  -p 10.10.10.1:8080:8080 \
  xuxueli/xxl-job-admin:2.4.1
```

## 5. RocketMQ（NameServer + Broker）
```bash
docker run -d \
  --name rmqnamesrv \
  --restart unless-stopped \
  --network middleware_net \
  -p 10.10.10.1:9876:9876 \
  apache/rocketmq:5.3.1 \
  sh mqnamesrv
```

```bash
docker run -d \
  --name rmqbroker \
  --restart unless-stopped \
  --network middleware_net \
  -e NAMESRV_ADDR=rmqnamesrv:9876 \
  -p 10.10.10.1:10911:10911 \
  -p 10.10.10.1:10909:10909 \
  apache/rocketmq:5.3.1 \
  sh mqbroker -n rmqnamesrv:9876 --enable-proxy
```

## 6. 检查
```bash
docker ps
docker logs --tail 100 postgres
docker logs --tail 100 redis
docker logs --tail 100 mysql
docker logs --tail 100 xxl-job-admin
docker logs --tail 100 rmqnamesrv
docker logs --tail 100 rmqbroker
```

## 7. 连接地址（WireGuard 内）
- PostgreSQL: `10.10.10.1:5432`
- Redis: `10.10.10.1:6379`
- MySQL: `10.10.10.1:3306`
- XXL-Job Admin: `http://10.10.10.1:8080/xxl-job-admin`
- RocketMQ NameServer: `10.10.10.1:9876`
- RocketMQ Dashboard: http://10.10.10.1:8081
