#!/usr/bin/env bash
# smoke-test.sh — 后端启动冒烟（Phase 0；后续阶段逐步追加业务链路检查）
set -eu

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "== smoke-test: $BASE_URL =="
health=$(curl -s --max-time 5 "$BASE_URL/actuator/health")
echo "health: $health"
case "$health" in
  *'"UP"'*) echo "✅ UP";;
  *) echo "❌ health 非 UP"; exit 1;;
esac
