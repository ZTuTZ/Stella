# API_V1 — 接口契约

> 状态：Phase 0 建立。各 Phase 完成对应模块时同步更新本文件，使其始终与代码一致。

## 约定

- 统一前缀：`/api/v1`
- 统一响应体：

```json
{ "success": true, "code": "OK", "message": "", "data": {}, "requestId": "..." }
```

- 错误时 `success=false`，HTTP 状态码与业务错误码同时使用；前端不展示底层异常栈。
- 需登录接口携带 `Authorization`；创建生成任务用 `Idempotency-Key` 头。

## 错误码（§22）

`AUTH_REQUIRED` `AUTH_INVALID_TOKEN` `WECHAT_LOGIN_FAILED` `USER_DISABLED` `PROFILE_NOT_FOUND` `GOAL_NOT_FOUND` `GOAL_CONTENT_BLOCKED` `STORY_NOT_FOUND` `TASK_NOT_FOUND` `TASK_ALREADY_RUNNING` `TASK_RETRY_LIMIT_REACHED` `TEXT_AI_UNAVAILABLE` `TEXT_AI_INVALID_RESPONSE` `AUDIO_PROVIDER_NOT_CONFIGURED` `AUDIO_SUBMIT_FAILED` `AUDIO_PROCESSING_FAILED` `AUDIO_STORAGE_FAILED` `MODERATION_BLOCKED` `RATE_LIMITED` `VALIDATION_FAILED` `INTERNAL_ERROR`

## 接口清单

### Auth
| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| POST | `/api/v1/auth/wechat/login` | code 登录，返回 accessToken + user | ✅ Phase 2（mock/真实二合一） |
| POST | `/api/v1/admin/auth/login` | 管理后台登录（BCrypt + admin token） | ✅ Phase 8 |

### Me / Profile
| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| GET / PATCH | `/api/v1/me` | 当前用户 / 更新 displayName(1~30) | ✅ Phase 2 |
| GET / PUT | `/api/v1/me/life-profile` | 人生档案（PUT 全量替换，版本 +1） | ✅ Phase 2 |
| POST | `/api/v1/me/onboarding/complete` | 完成 onboarding（幂等） | ✅ Phase 2 |

### Goals
| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| POST / GET | `/api/v1/goals` | 创建（分类/标题2~80/描述10~1200）/ 列表 | ✅ Phase 3 |
| GET / PATCH / DELETE | `/api/v1/goals/{id}` | 详情 / 修改 / 软删除（越权一律 404） | ✅ Phase 3 |
| POST | `/api/v1/goals/{id}/followup-questions` | AI 追问（0~3 题，mock/openai 可切） | ✅ Phase 3 |
| PUT | `/api/v1/goals/{id}/followup-answers` | 保存回答 → details_json | ✅ Phase 3 |

### Generation
| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| POST | `/api/v1/goals/{id}/generate-story` | 创建生成任务（Idempotency-Key 幂等；运行中 409；输入拦截 422） | ✅ Phase 4 |
| GET | `/api/v1/generation-tasks/{id}` | 任务状态/进度/audioStatus | ✅ Phase 4/5 |

### Stories
| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| GET | `/api/v1/stories?page&size&favorite` | 分页列表（favorite=true 仅收藏；created 倒序） | ✅ Phase 7 |
| GET | `/api/v1/stories/{id}` | 详情（audio: {status,url,durationMs,format}） | ✅ Phase 4/5 |
| PUT / DELETE | `/api/v1/stories/{id}/favorite`、`/api/v1/stories/{id}` | 收藏 / 软删除 | ✅ Phase 7 |
| POST | `/api/v1/stories/{id}/retry-audio` | 仅重试音频（在途幂等返回；终态重置回 AUDIO_SUBMITTING） | ✅ Phase 5 |

### Affirmations / Home
| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| GET | `/api/v1/affirmations/today` | 当日幂等生成（UNIQUE(user,date) 兜底并发；输出过审核） | ✅ Phase 7 |
| GET | `/api/v1/home` | 首页聚合：昵称+今日/最近故事+肯定句+运行中任务 | ✅ Phase 7 |

### Admin（`/api/v1/admin/*`，全部需 admin token；USER token → 403 AUTH_FORBIDDEN）
| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| POST | `/admin/auth/login` | 管理后台登录 | ✅ Phase 8 |
| GET | `/admin/users?keyword&page&size` | 用户分页（openid 脱敏） | ✅ Phase 8 |
| GET | `/admin/users/{id}` | 详情：基础+档案+目标+故事+最近任务 | ✅ Phase 8 |
| PUT | `/admin/users/{id}/status` | 封禁/解封（token 立即失效） | ✅ Phase 8 |
| GET | `/admin/goals /stories /tasks`（userId/status/page/size） | 内容与任务分页 | ✅ Phase 8 |
| POST | `/admin/tasks/{id}/retry` | 手动重试（文本失败→重生成；有故事→只重试音频；BLOCKED 不可重试） | ✅ Phase 8 |
| GET/POST | `/admin/prompts`、`/admin/prompts/{id}` | 版本列表/详情/新建（默认禁用） | ✅ Phase 8 |
| PUT | `/admin/prompts/{id}/enable /disable` | 切换版本（同 key 仅一个 enabled） | ✅ Phase 8 |
| GET | `/admin/ai-logs`（requestId/operation/providerType） | AI 调用日志 | ✅ Phase 8 |
| GET | `/admin/dashboard/summary` | §20.1 全部指标 | ✅ Phase 8 |

> 新增错误码 `AUTH_FORBIDDEN(403)`（§22 基础上的补充）。

## 限流（§24）

创建 Goal 30 次/小时；追问 20 次/小时；生成故事 10 次/日；重试音频 5 次/故事/日。幂等键 Redis TTL 24h。

## V1.0.1 变更说明（H-03）

- `GET /api/v1/stories/{id}` 中 `audio.url` 为**短时读取 URL**（默认 15 分钟，`STORAGE_READ_URL_TTL_MINUTES`）：
  - local/test：`/media/audio/...`（仅本地开发路由）
  - prod：OSS 预签名 URL（bucket 私有读）
- `audio.url` 仅在 `status=READY` 且存在 `storage_key` 时返回；生成中返回 `null` + 最新状态。
- 重新生成音频期间：若存在旧 READY 音频，仍返回旧的可用 URL（blue-green），新音频 READY 后自动切换。
