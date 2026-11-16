# DATABASE_V1 — 数据模型与 Migration

> 状态：Phase 0 建立。Phase 1 完成后记录 migration 清单；此后每次新增 migration 都要在此登记。

## 约定

- 数据库：MySQL 8，库名 `future_self`，`utf8mb4 / utf8mb4_0900_ai_ci`。
- 结构变更只通过新增 Flyway migration：`backend/src/main/resources/db/migration/`，禁止修改历史文件。
- 业务表统一基础字段：`id BIGINT UNSIGNED AUTO_INCREMENT PK`、`created_at DATETIME(3)`、`updated_at DATETIME(3)`。
- 所有删除默认软删除；Redis 不保存任务/故事/音频状态。

## 核心表（Phase 1 目标结构，详见实施文档 §10）

| 表 | 用途 | 关键约束/索引 |
|---|---|---|
| `app_user` | 小程序用户 | UNIQUE openid；idx last_active_at |
| `life_profile` | 人生档案（一对一） | UNIQUE user_id；profile_version 修改 +1 |
| `manifestation_goal` | 目标/愿望 | idx(user_id,status,created_at)；details_json 存追问答案 |
| `story` | 故事 | 记录 prompt_template_id/provider/model/profile_version；moderation_status |
| `audio_asset` | 音频资产 | status: PENDING/SUBMITTED/PROCESSING/READY/FAILED；storage_key 自有存储 |
| `generation_task` | 生成任务队列 | idx(status,next_retry_at,created_at)；retry_count/max_retry_count/next_retry_at |
| `affirmation_daily` | 每日肯定句 | UNIQUE(user_id, local_date) |
| `prompt_template` | Prompt 版本 | UNIQUE(prompt_key, version)；同 key 仅一个 enabled |
| `ai_call_log` | AI 调用诊断日志 | 不存 API key；存 request/response 摘要 |
| `admin_user` | 后台管理员 | 独立凭证，不与小程序用户混用 |
| `user_event`（可选） | 产品行为埋点 | 只记产品行为，不存故事正文 |

## Generation Task 状态机（§11）

```text
PENDING → TEXT_GENERATING → TEXT_MODERATING → TEXT_READY
        → AUDIO_SUBMITTING → AUDIO_PROCESSING → AUDIO_STORING → COMPLETED
失败：RETRY_WAIT → 回到失败阶段；超过 max_retry_count → FAILED
审核拦截：TEXT_MODERATING → BLOCKED
```

## Migration 清单

| 版本 | 文件 | 内容 | 状态 |
|---|---|---|---|
| V1 | （Phase 1 创建） | 全部核心表 + Prompt seed | 计划中 |
