#!/usr/bin/env bash
# dev-check.sh — 本地开发环境自检（Phase 0）
# 用途：快速确认 JDK/Maven/MySQL/Redis/Node 就绪。
set -u

pass=0; fail=0
check() { # check <说明> <命令...>
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then
    echo "  ✅ $desc"; pass=$((pass+1))
  else
    echo "  ❌ $desc"; fail=$((fail+1))
  fi
}

echo "== dev-check =="
if [ -x "/Users/aadmin/tool/apache-maven-3.9.16/bin/mvn" ]; then
  check "Maven 3.9.16 (/Users/aadmin/tool/apache-maven-3.9.16)" /Users/aadmin/tool/apache-maven-3.9.16/bin/mvn -version
else
  check "Maven (PATH)" mvn -version
fi
check "JDK 21" java -version
check "MySQL 连接（读 .env 或环境变量 DB_PASSWORD）" sh -c 'test -n "${DB_PASSWORD:-}" && mysql -h127.0.0.1 -u"${DB_USERNAME:-root}" -p"$DB_PASSWORD" -e "SELECT 1"'
check "Redis PING" redis-cli ping
check "Node >= 22" node -e 'process.exit(parseInt(process.versions.node.split(".")[0],10)>=22?0:1)'

echo "== 结果: pass=$pass fail=$fail =="
if [ "$fail" -gt 0 ]; then exit 1; fi
