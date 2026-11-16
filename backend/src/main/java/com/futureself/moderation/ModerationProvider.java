package com.futureself.moderation;

/**
 * 内容审核接口（实施文档 §19.2/§19.3）。
 * 阶段：Goal 输入 → LLM 故事输出 → Affirmation 输出（Phase 7）。
 * 规则实现只是兜底；保留未来替换真实审核服务的能力（不改业务层）。
 */
public interface ModerationProvider {

    ModerationResult check(ModerationRequest request);
}
