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

- [x] Phase 0 — 项目骨架（backend 可启动 /actuator/health；miniapp 空首页 + tokens；admin-web 可构建）
- [x] Phase 1 — 数据库与基础设施（Flyway V1 全部 11 表；统一响应/错误码/requestId；Storage 本地实现 + 受控 /media）
- [x] Phase 2 — Auth + User + Profile（mock/真实微信登录、opaque token、Me/LifeProfile、onboarding）
- [x] Phase 3 — Goal + AI Followup（Goal CRUD、TextAiProvider mock/openai、Prompt v1 seed、追问全流程、ai_call_log）
- [x] Phase 4 — Story Generation（任务状态机+worker、STORY prompt、输入/输出规则审核、失败重试、重启恢复、故事详情）
- [x] Phase 5 — AI Audio Framework（AiAudioProvider+Mock、audio_asset、音频阶段状态机、Storage 转存、retry-audio、supersede）
- [ ] Phase 6 — 真实音频 API（条件阶段，等 docs/external/audio/）
- [ ] Phase 4 — Story Generation
- [ ] Phase 5 — AI Audio Framework
- [ ] Phase 6 — 真实音频 API（条件阶段）
- [ ] Phase 7 — Affirmation + Home + Story Library
- [ ] Phase 8 — 管理后台 + 稳定性
- [ ] Phase 9 — 发布前清单

## 常见问题

见 [docs/RUNBOOK.md](./docs/RUNBOOK.md)。
