package com.futureself.ai.text;

/**
 * 文本 AI 统一接口（实施文档 §13.1）。
 * 业务层不得直接调用第三方 HTTP API；所有实现经此接口注入。
 */
public interface TextAiProvider {

    /** 结构化生成：把模型返回反序列化为 responseType。失败抛 BusinessException。 */
    <T> T generateStructured(TextAiRequest request, Class<T> responseType);

    /** provider 标识（写入 ai_call_log / story.provider）。 */
    String providerName();
}
