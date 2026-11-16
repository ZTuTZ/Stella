package com.futureself.ai.audio;

import java.util.Map;

/**
 * 音频生成请求（实施文档 §15.2）。text 为 AudioScriptBuilder 处理后的旁白文本。
 */
public record AudioGenerationRequest(
        String requestId,
        Long storyId,
        String text,
        String voiceId,
        String mood,
        String pace,
        AudioMode mode,
        Map<String, Object> options) {
}
