# 音频 API 文档目录

用户后续把 AI 音频供应商文档放入本目录。**在本目录出现真实文档之前**：

- 禁止猜测真实音频 API 的 endpoint、鉴权 header、签名算法。
- 禁止编写任何"看起来像真实 Provider"的伪实现。
- 只实现并使用 `MockAiAudioProvider`。

文档到位后，按实施文档 §15.5 / §37 执行：先读全文档 → 产出 `docs/API_AUDIO_PROVIDER_MAPPING.md` → 实现新 Provider（不改业务层接口）→ 脱敏日志 → 结果转存 → 测试 → 更新 `.env.example` 与 RUNBOOK。
