# Phase 9 — V1 发布前清单核对（2026-11-17）

> 依据实施文档 §28 Phase 9。每项均为实际验证，非主观打勾。

| # | 项目 | 验证方式 | 结果 |
|---|---|---|---|
| 1 | 所有 secret 均来自环境变量 | `git ls-files` 无 .env/密钥文件；application.yml 全部 `${ENV:}` 占位（DB/Redis/微信/AI/admin） | ✅ |
| 2 | 无测试账号密码写死 | 主代码 grep 无硬编码密码/密钥；`application-test.yml` 的 bootstrap 密码仅在测试 classpath 且只写测试库；bootstrap 本身仅在有 env 密码时运行 | ✅ |
| 3 | 无 provider API key 进入前端包 | miniapp/admin-web grep 无任何 provider key（admin token 为运行时 会话，非密钥） | ✅ |
| 4 | 用户资源接口 owner 校验 | goals/stories/tasks/followup/generate/retry-audio 全部经 `loadOwnGoal/loadOwnTask/findByIdAndUserId...`；越权一律 404（测试覆盖） | ✅ |
| 5 | AI 结果标识 | 故事详情 `aiGenerated:true` + 详情页徽标；播放器「AI 内容」；首页肯定句区「AI 生成内容」（本轮补齐）；miniapp 全局无伪造数据 | ✅ |
| 6 | 音频 URL HTTPS | dev 为 `http://localhost:8080/media/**`（本地联调）；`services/config.ts` 注明生产必须 https + 小程序合法域名；媒体路由仅暴露业务媒体 | ✅（生产条件已文档化） |
| 7 | 文本可独立于音频使用 | Phase 4/5 测试：音频 RETRY_WAIT/FAILED 期间文本始终可读（§32.6） | ✅ |
| 8 | 任务有重试上限 | max_retry_count=3（可配）；`RETRY_WAIT→FAILED` 路径有测试（AudioFlow 集成） | ✅ |
| 9 | 日志不打印 token/secret/session_key | 全部 log 语句 grep 复查；仅输出 errcode/异常类名/脱敏摘要；AI 日志存截断摘要 | ✅ |
| 10 | 全新 MySQL 8 迁移成功 | `future_self_fresh` 库实际重放：12 张表 + prompt seed 3 条 + health UP（验证后已删除） | ✅ |
| 11 | README 含启动方式 | 快速开始/建库/配置/启动顺序/Mock 说明齐备 | ✅ |
| 12 | AGENTS.md 与代码一致 | 规则均有效：Flyway-only、Adapter 隔离、无伪造真实音频 Provider、每阶段提交前测试 | ✅ |

## §39 最终验收场景（15 步）覆盖情况

- 步骤 1~13（登录→Onboarding→目标→追问→生成→文本先读→音频完成→播放→收藏→首页→再次进入数据仍在）：后端链路已在各 Phase 冒烟测试中完整走通（mock 数据）；小程序端代码已实现，**需在微信开发者工具人工过一遍**。
- 步骤 14（后台定位用户/目标/故事/任务/AI 日志）：`AdminIntegrationTest` + 冒烟验证 ✅。
- 步骤 15（模拟音频失败→后台重试→最终完成）：`AudioFlowIntegrationTest.retryAudioAfterFinalFailure` + 后台 retry 接口验证 ✅。

## 遗留（不阻塞内测）

- `goal/detail` 页与 Library「最近播放」横行为可选项，未实现。
- Prompt 在线试跑为 P1（§20.3 明确 V1 可不做）。
- 音频 URL 生产 https 与小程序合法域名配置，在发布时执行。
