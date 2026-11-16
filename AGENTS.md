# AGENTS.md — 本地 Agent 工作守则

> 本文件写给所有参与 `future-self-mini`（Stella 仓库）开发的本地 Agent。开工前必须先阅读根目录 `future-self-mini_local-agent-development-spec_v1.1.md` 与 `docs/` 下全部文档。

## 必须遵守

1. **先读文档再动手**：任何任务开始前，先读本实施文档和相关 `docs/` 章节；文档是开发合同，不得自行扩展 V1 范围（P1/P2 明确不做）。
2. **数据库变更只走 Flyway**：修改表结构必须新建 migration（`backend/src/main/resources/db/migration/`），禁止修改历史 migration 文件；`future_self` 库为唯一事实源。
3. **音频 API 红线**：`docs/external/audio/` 中没有真实供应商文档时，禁止猜测 endpoint、鉴权 header、签名方式，禁止编写"看起来像真实 Provider"的伪实现；只允许 `MockAiAudioProvider` + 完整业务框架。
4. **外部 API 一律封装 Adapter**：第三方 HTTP 调用只能出现在 `ai/text`、`ai/audio`、`moderation`、`storage` 等适配层，Controller 不得直接调用第三方 API。
5. **Secret 管理**：API key、微信 app secret、数据库密码一律走环境变量 / `.env`（已 gitignore），禁止写入 Git、日志或前端包。
6. **每阶段验收后提交**：每个 Phase（或独立任务单元）完成时：编译、`mvn test`、前端类型检查全部通过，修复失败，同步更新 README / docs，然后 git commit。一次提交只做一个逻辑单元。
7. **权限校验**：所有按 ID 访问的用户资源接口必须再次校验 `owner_user_id`，不得只依赖前端传参。
8. **可观测性**：所有生成链路带 `requestId`；日志禁止输出 API key、app secret、完整 token/session_key、完整用户隐私正文。
9. **Mock 不走捷径**：Mock Provider 模式必须走真实数据库与任务状态机，只替换第三方服务响应。
10. **每次提交前更新相关文档**（README/API/docs），保持 `AGENTS.md` 与代码现状一致。

## 禁止事项（摘自文档 §36）

- 自行加入支付、会员、社区、签到等非 P0 功能。
- 把 AI 调用放在数据库长事务内；无限制自动重试第三方服务。
- 用 Redis 代替数据库保存任务/故事/音频状态。
- 把 prompt 只写死在 Java 字符串里而不做版本记录。
- 为架构感拆微服务。

## 提交规范

- 分支：`main`；提交信息用 `type: summary`（chore/feat/fix/docs/test/refactor）。
- 每完成一个 Phase 推送一次；禁止提交 `node_modules/`、`target/`、`.env`、`data/`、`.DS_Store`。
