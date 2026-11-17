# Stella — Future Self 微信小程序 V1

根据用户真实背景、重要人物和目标，生成"目标已经实现后的未来生活故事"，再用 AI 生成沉浸式旁白音频，让用户每天听一段"未来的自己"。

- 项目代号：`future-self-mini`（仓库：Stella）
- 开发合同：[`future-self-mini_local-agent-development-spec_v1.1.md`](./future-self-mini_local-agent-development-spec_v1.1.md)
- Agent 守则：[AGENTS.md](./AGENTS.md) · 运行手册：[docs/RUNBOOK.md](./docs/RUNBOOK.md)

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 `backend/` | Java 21 · Spring Boot 4.1.x 单体 · Spring Security/JPA · Flyway |
| 数据库 | MySQL 8（唯一事实源）· Redis（缓存/限流/幂等/锁） |
| 存储 | 本地文件（dev）/ 阿里云 OSS（`STORAGE_PROVIDER=oss`，纯 REST + V1 签名） |
| 小程序 `miniapp/` | 微信原生 + TypeScript + WXML/WXSS |
| 管理后台 `admin-web/` | Vue 3 + TS + Vite + Pinia + Element Plus |

## 目录结构

```text
backend/    Spring Boot 后端（com.futureself）
miniapp/    微信小程序（原生 + TS）
admin-web/  Vue 3 管理后台
docs/       开发文档（API/DATABASE/PROMPTS/RUNBOOK/external/audio）
scripts/    dev-check.sh / smoke-test.sh
```

## 快速开始

1. **环境**：JDK 21、Maven 3.9+、MySQL 8、Redis、Node ≥22（nvm）。
2. **建库**（只建空库，表由 Flyway 创建）：

   ```sql
   CREATE DATABASE IF NOT EXISTS future_self
     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   ```

3. **配置**：`cp .env.example .env` 并填入本地值（`.env` 不进 Git）。
4. **启动后端**：`cd backend && mvn spring-boot:run`，健康检查 `http://localhost:8080/actuator/health`。
5. **管理后台**：`cd admin-web && npm install && npm run dev`。
6. **小程序**：微信开发者工具导入 `miniapp/` 目录。

## Mock AI 模式

无任何 AI Key 时项目可完整演示：默认 `AI_TEXT_PROVIDER=mock`、`AI_AUDIO_PROVIDER=mock`。Mock 走真实数据库与任务状态机，仅替换第三方服务响应。

## 切换真实音频 Provider

将供应商文档放入 `docs/external/audio/` 后按实施文档 §15.5/§37 执行；此前禁止猜测真实 API。文档到位并实现后，改 `AI_AUDIO_PROVIDER=<name>` 即可切换。

## 测试

```bash
cd backend && mvn test
cd admin-web && npm run build
```

## 状态

### V1 功能阶段

- [x] Phase 0 — 项目骨架（backend 可启动 /actuator/health；miniapp tokens；admin-web 可构建）
- [x] Phase 1 — 数据库与基础设施（Flyway V1 11 表；统一响应/错误码/requestId；本地 Storage + 受控 /media）
- [x] Phase 2 — Auth + User + Profile（mock/真实微信登录、opaque token、Me/LifeProfile、onboarding）
- [x] Phase 3 — Goal + AI Followup（Goal CRUD、TextAiProvider mock/openai、Prompt v1 seed、追问全流程、ai_call_log）
- [x] Phase 4 — Story Generation（任务状态机 + worker、STORY prompt、输入/输出审核、失败重试、重启恢复、故事详情）
- [x] Phase 5 — AI Audio Framework（AiAudioProvider + Mock、audio_asset、音频状态机、Storage 转存、retry-audio）
- [x] Phase 6 — 真实音频 Provider（腾讯云 TTS：`tencent` 长文本异步 + `tencent-sync` 基础同步分段，已真实联调出声）
- [x] Phase 7 — Affirmation + Home + Story Library（当日幂等肯定句、首页聚合、分页/收藏/软删除、user_event 埋点）
- [x] Phase 8 — 管理后台 + 稳定性（admin 8 页、任务重试、Prompt 切换、AI 日志、Dashboard、四类限流、音频清理）
- [x] Phase 9 — 发布前检查清单（docs/PHASE9_CHECKLIST.md）
- [x] 小程序 UI Phase 0~4（设计系统/Onboarding/Home/沉浸播放器/Library+Profile+设置/custom-tab-bar）
- [x] 管理后台 UI 重做（Element Plus + 全中文状态 + 表格/详情/筛选/自动刷新）

### V1.0.1 Hardening（2026-11）

- [x] H-01 首页 Affirmation 故障隔离（失败返回空列表，不再 NPE/500）
- [x] H-02 限流改为真实固定小时窗口（Asia/Shanghai），幂等重放不消耗配额
- [x] H-03 私人音频改为短时读取 URL（OSS 预签名）；本地 `/media` 仅 local/test
- [x] H-04 Storage 失败只重试转存，不重复调用 TTS
- [x] H-05 音频 Blue-Green 切换：新音频 READY 后才淘汰旧音频；SUPERSEDED 按保留期清理
- [x] H-06 生成任务持久幂等（V3 + goal 行锁），Redis 仅作缓存
- [x] H-07 AI 日志只保留 operation/长度/hash，不保存正文
- [x] H-08 音频结果 URL 校验（file 白名单目录 / https + host allowlist / 私网拒绝）
- [x] H-09 STORY / FOLLOWUP / AFFIRMATION 最终上下文统一审核
- [x] H-10 生产 profile + 启动 fail-fast；小程序按 envVersion 区分 API 域名，release 不回退 localhost
- [x] H-11 管理端登录失败限流（IP + usernameHash）
- [x] H-12 GitHub Actions CI（backend / admin-web / miniapp / secret scan）

### 生产约束（必须遵守）

- `SPRING_PROFILES_ACTIVE=prod` 启动；mock provider / local storage / 空密钥会**直接启动失败**（见 docs/RUNBOOK.md）。
- OSS bucket 建议私有读，播放走短时签名 URL；小程序需配置合法域名 + HTTPS。
- 后端 worker 目前**仅支持单实例**（replica = 1）。

## 常见问题

见 [docs/RUNBOOK.md](./docs/RUNBOOK.md)。
