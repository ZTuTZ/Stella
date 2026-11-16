# PROMPTS — Prompt 版本与结构化输出

> 状态：Phase 0 建立；Phase 3 起，Prompt 文本一律写入 Flyway seed migration，本文件只登记版本元数据。

## 约定

- Prompt 不写死在 Java 字符串里；版本存 `prompt_template` 表（prompt_key + version + enabled）。
- 同一 `prompt_key` 仅一个 `enabled=true`；后台编辑即新版本，不覆盖历史。
- Story 记录 `prompt_template_id`、provider、model，便于回溯。
- 上下文组装（§33）：空字段不发送、限制总长、不发管理员备注/openid/userId/历史完整故事。

## Prompt Key 登记表

| key | 用途 | 输出结构 | 版本 | 状态 |
|---|---|---|---|---|
| `FOLLOWUP` | 基于用户输入生成 0~3 个追问 | `{questions:[{id,question,reason,optional}]}` | v1 | ✅ 已 seed（V2 migration）并在用 |
| `STORY` | 未来故事（第二人称，900~1800 字） | `{title,summary,content,audioDirection:{mood,pace,notes}}` | v1 | ✅ 已 seed（Phase 4 启用） |
| `AFFIRMATION` | 每日 6 条肯定句（12~40 字/条，第一人称） | `{items:[...]}` | v1 | ✅ 已 seed（Phase 7 启用） |

## 写作红线

- 故事是"想象/自我激励"，不是预测，不做真实性保证；高风险领域（医疗/投资/法律/死亡）不写成确定事实。
- 信息不足不编造真实姓名、住址、公司名；不问身份证/地址/财务账户等高敏感信息。
- 语气温柔克制，具体细节多于抽象鸡汤，不用夸张神谕式表达。
