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
| POST | `/api/v1/admin/auth/login` | 管理后台登录 | 计划 Phase 8 |

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
| POST | `/api/v1/goals/{id}/generate-story` | 创建生成任务 | 计划 Phase 4 |
| GET | `/api/v1/generation-tasks/{id}` | 任务状态/进度 | 计划 Phase 4 |

### Stories
| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| GET | `/api/v1/stories?page&size&favorite` | 列表 | 计划 Phase 7 |
| GET | `/api/v1/stories/{id}` | 详情（含 audio 状态） | 计划 Phase 4/7 |
| PUT / DELETE | `/api/v1/stories/{id}/favorite`、`/api/v1/stories/{id}` | 收藏 / 软删除 | 计划 Phase 7 |
| POST | `/api/v1/stories/{id}/retry-audio` | 仅重试音频 | 计划 Phase 5 |

### Affirmations / Home
| 方法 | 路径 | 说明 | 状态 |
|---|---|---|---|
| GET | `/api/v1/affirmations/today` | 当日幂等生成 | 计划 Phase 7 |
| GET | `/api/v1/home` | 首页聚合 | 计划 Phase 7 |

### Admin（`/api/v1/admin/*`）
`users` `users/{id}` `goals` `stories` `tasks` `tasks/{id}/retry` `prompts` `prompts/{id}` `ai-logs` `dashboard/summary` — 计划 Phase 8。

## 限流（§24）

创建 Goal 30 次/小时；追问 20 次/小时；生成故事 10 次/日；重试音频 5 次/故事/日。幂等键 Redis TTL 24h。
