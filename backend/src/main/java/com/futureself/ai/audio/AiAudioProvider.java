package com.futureself.ai.audio;

/**
 * AI 音频统一接口（实施文档 §15.2）。
 * 真实供应商文档到位前只有 Mock 实现；禁止猜测真实 API 的 endpoint/鉴权/签名。
 * 业务层通过 capabilities 封装差异；所有 secret 走环境变量。
 */
public interface AiAudioProvider {

    AudioProviderCapabilities capabilities();

    AudioSubmitResult submit(AudioGenerationRequest request);

    AudioQueryResult query(String providerTaskId);

    default boolean supportsWebhook() {
        return capabilities().supportsWebhook();
    }

    String providerName();
}
