package com.futureself.ai.audio;

/**
 * 供应商能力声明（实施文档 §15.3）。
 * 生成服务通过 capabilities 封装差异，不写 if(providerName.equals(...)) 特例。
 */
public record AudioProviderCapabilities(
        boolean async,
        boolean supportsWebhook,
        boolean supportsNarrationMix,
        boolean supportsVoiceSelection,
        boolean supportsSpeed,
        boolean resultUrlMayExpire) {
}
