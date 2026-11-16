package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 音频配置（app.ai.audio，实施文档 §15/§21）。
 * provider: mock（唯一实现，真实 Provider 待 docs/external/audio/ 文档）。
 * mock-delay-ms：Mock submit → READY 的模拟生成时长；mock-fixture-dir：测试音频目录。
 */
@ConfigurationProperties("app.ai.audio")
public record AppAudioProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String voiceId,
        long mockDelayMs,
        String mockFixtureDir) {

    public AppAudioProperties {
        if (provider == null || provider.isBlank()) {
            provider = "mock";
        }
        if (baseUrl == null) {
            baseUrl = "";
        }
        if (apiKey == null) {
            apiKey = "";
        }
        if (voiceId == null) {
            voiceId = "";
        }
        if (mockDelayMs < 0) {
            mockDelayMs = 3000;
        }
        if (mockFixtureDir == null || mockFixtureDir.isBlank()) {
            mockFixtureDir = "./data/mock-audio";
        }
    }
}
