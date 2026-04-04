#!/usr/bin/env bash
set -euo pipefail

docker rm -f xxl-job-admin >/dev/null 2>&1 || true
docker run -d --name xxl-job-admin --restart unless-stopped \
  --network middleware_net \
  -e "PARAMS=--spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&serverTimezone=Asia/Shanghai --spring.datasource.username=root --spring.datasource.password=123456 --xxl.job.accessToken=123456" \
  -v xxl_job_logs:/data/applogs \
  -p 10.10.10.1:8080:8080 \
  xuxueli/xxl-job-admin:2.4.1

