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
cd backend && mvn -s ../scripts/maven-settings.xml test          # 后端单元/集成测试（需本地 MySQL+Redis）
cd admin-web && npm run build   # 前端类型检查 + 构建
```

> 集成测试连接独立测试库 `future_self_test`（自动创建），Redis 使用 db15；需先 `source .env` 或在环境变量中提供 `DB_PASSWORD`。

## Maven 注意事项

本机全局 settings.xml 激活了云效私有仓库 profile（仅 deploy 用），其域名对 JVM TLS 握手失败会导致依赖解析中断。
**本项目构建统一使用项目级配置**：`mvn -s scripts/maven-settings.xml ...`（只走阿里云公共镜像，本地仓库路径与全局一致）。

## 音频 API 文档

放入 `docs/external/audio/` 后，按实施文档 §15.5 / §37 执行真实 Provider 接入。

## Mock 音频

`AI_AUDIO_PROVIDER=mock`（默认）时：submit 即返回模拟 taskId，`AI_AUDIO_MOCK_DELAY_MS`（默认 3000）后 READY；
READY 返回项目内 fixture（`AI_AUDIO_MOCK_FIXTURE_DIR`，启动时自动生成 2 秒静音 WAV），由后端转存到自有存储后经 `/media/**` 播放。
**真实音频 Provider 未实现**：`docs/external/audio/` 出现供应商文档前，任何非 mock 的 provider 值会因无 Bean 而启动失败（明确的配置错误）。

## 管理后台

- 凭据：`.env` 的 `ADMIN_BOOTSTRAP_USERNAME/PASSWORD`；首次启动 bootstrap 自动建 admin（BCrypt）。本地默认密码见 `.env`，**请自行修改**。
- 启动：`cd admin-web && npm run dev` → http://localhost:5173（/api 代理到 8080）。
- 能力：用户检索/封禁、目标/故事/任务列表、失败任务手动重试（音频失败只重试音频）、Prompt 版本切换、AI 日志按 requestId 排查、Dashboard 指标。

## 限流与清理

- 限流（§24，环境变量可调）：Goal 30/时、追问 20/时、生成 10/日、重试音频 5/日/故事；Redis 不可用时降级放行。
- 音频清理（§25.2）：每日 03:05 物理删除超期（默认 30 天）已删除故事的音频文件。
- 注销（§25.3）：`UserDeletionService.requestDeletion(userId)` 已预留（禁用账号+软删数据），UI 暂不开放。

## Mock 微信登录

`WECHAT_MOCK_LOGIN=true`（默认 `.env` 已开）时，`POST /api/v1/auth/wechat/login` 不访问微信：
code 确定性映射为 `mock_<sha256前24位>` openid，同一 code 恒为同一用户。联调真实微信时改为 false 并配置 `WECHAT_APP_ID/WECHAT_APP_SECRET`。

## 鉴权说明

opaque token 存 Redis（key `fs:auth:token:<sha256>`，TTL 7200s）；Redis 不可用时鉴权 fail-closed（401）。

## 常见问题

- **MySQL 拒绝连接/密码错误**：检查 `.env` 的 `DB_USERNAME/DB_PASSWORD`；本机 root 密码由用户本地保存。
- **pnpm 报 Node 版本不符**：`nvm use 22`（已设 default 22.22.0）。
- **端口占用**：backend 默认 8080，admin-web dev 默认 5173。
