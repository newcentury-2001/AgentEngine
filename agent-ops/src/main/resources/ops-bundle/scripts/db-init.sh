#!/usr/bin/env bash
set -euo pipefail

# Wait MySQL ready (max ~32s)
mysql_ready=0
for i in $(seq 1 8); do
  if docker exec mysql mysqladmin -uroot -p123456 ping --silent >/dev/null 2>&1; then
    mysql_ready=1
    break
  fi
  sleep 4
done
if [ "$mysql_ready" -ne 1 ]; then
  echo "mysql not ready after retries" >&2
  exit 1
fi

# Wait PostgreSQL ready (max ~32s)
postgres_ready=0
for i in $(seq 1 8); do
  if docker exec postgres pg_isready -U root -d postgres >/dev/null 2>&1; then
    postgres_ready=1
    break
  fi
  sleep 4
done
if [ "$postgres_ready" -ne 1 ]; then
  echo "postgres not ready after retries" >&2
  exit 1
fi

docker exec -i mysql mysql -uroot -p123456 -D xxl_job < /opt/agent-ops/sql/init_xxl_mysql.sql
docker exec -i mysql mysql -uroot -p123456 -D xxl_job < /opt/agent-ops/sql/init_log_mysql.sql
docker exec -i postgres psql -U root -d postgres < /opt/agent-ops/sql/init_skill_pg.sql
docker exec -i postgres psql -U root -d postgres < /opt/agent-ops/sql/init_slot_pg.sql

# Wait slot_definition table visible, then seed inserts
for i in $(seq 1 5); do
  if docker exec -i postgres psql -U root -d postgres -tAc "SELECT to_regclass('public.slot_definition') IS NOT NULL;" | grep -q t; then
    break
  fi
  sleep 1
done
docker exec -i postgres psql -U root -d postgres < /opt/agent-ops/sql/init_slot_seed_pg.sql
