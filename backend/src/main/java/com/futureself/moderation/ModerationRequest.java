package com.futureself.moderation;

/**
 * 内容审核请求（实施文档 §19.2）。V1 阶段：GOAL_INPUT（目标输入）/ STORY_OUTPUT（故事输出）。
 * text 为待审文本；只保存摘要，不回传原始长文。
 */
public record ModerationRequest(String stage, Long userId, String text) {
}
