#!/usr/bin/env bash
# dev-backend.sh — 启动后端（自动加载根目录 .env；也可直接 mvn spring-boot:run）
set -eu
cd "$(dirname "$0")/.."
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi
MAVEN_BIN="${MAVEN_BIN:-/Users/aadmin/tool/apache-maven-3.9.16/bin/mvn}"
exec "$MAVEN_BIN" -s scripts/maven-settings.xml -f backend/pom.xml spring-boot:run
