# RUNBOOK — 本地运行手册

> Phase 0 建立；随 Phase 更新。secret 一律走 `.env`（不进 Git）。

## 前置环境

| 依赖 | 版本 | 本机状态 |
|---|---|---|
| JDK | 21 | ✅ 21.0.12 |
| Maven | 3.9.x | ✅ `/Users/aadmin/tool/apache-maven-3.9.16`（本地仓库与 settings.xml 在该目录内） |
| MySQL | 8.x | ✅ 8.4.11（root 密码见本地 `.env`，勿外传） |
| Redis | 6/7 | ✅ 已运行（localhost:6379） |
| Node.js | ≥22（nvm 管理） | ✅ v22.22.0（`nvm alias default 22.22.0` 已设置） |
| 微信开发者工具 | 最新稳定版 | 待安装/由用户打开 |

## 初始化数据库

只需创建空库，表结构由 Flyway 自动建：

```sql
CREATE DATABASE IF NOT EXISTS future_self
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

## 配置

```bash
cp .env.example .env   # 然后填入本地 DB_PASSWORD 等非提交值
```

无 AI Key 时默认 `AI_TEXT_PROVIDER=mock`、`AI_AUDIO_PROVIDER=mock`，可完整演示（Mock 走真实数据库与状态机）。

## 启动顺序

```bash
# 1. MySQL / Redis（本机服务）
# 2. backend（端口 8080）
cd backend && ../apache-maven 3.9.16/bin/mvn spring-boot:run   # 或本机 mvn
# 3. admin-web（需要时）
cd admin-web && npm install && npm run dev
# 4. 微信开发者工具打开 miniapp/
```

健康检查：`GET http://localhost:8080/actuator/health`（Phase 0 起可用；DB/Redis 状态随 Phase 1 接入）。

## 测试

```bash
cd backend && mvn test          # 后端单元/集成测试
cd admin-web && npm run build   # 前端类型检查 + 构建
```

## 音频 API 文档

放入 `docs/external/audio/` 后，按实施文档 §15.5 / §37 执行真实 Provider 接入。

## 常见问题

- **MySQL 拒绝连接/密码错误**：检查 `.env` 的 `DB_USERNAME/DB_PASSWORD`；本机 root 密码由用户本地保存。
- **pnpm 报 Node 版本不符**：`nvm use 22`（已设 default 22.22.0）。
- **端口占用**：backend 默认 8080，admin-web dev 默认 5173。
