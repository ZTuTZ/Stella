package com.futureself.ai.text;

/**
 * 文本 AI 请求（实施文档 §13.1）。requestId 贯穿日志；outputSchema 为 JSON Schema 文本（可空）。
 */
public record TextAiRequest(
        String operation,
        String systemPrompt,
        String userPrompt,
        String outputSchema,
        Double temperature,
        Integer maxTokens,
        String requestId) {

    public TextAiRequest {
        if (temperature == null) {
            temperature = 0.7;
        }
        if (maxTokens == null || maxTokens <= 0) {
            maxTokens = 2000;
        }
    }
}
