package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 音频配置（app.ai.audio，实施文档 §15/§21）。
 * provider: mock（本地演示）| tencent（腾讯云长文本语音合成，真实 Provider）。
 * 腾讯云凭据只从环境变量/.env 读取，禁止入库/入日志。
 * mock-delay-ms / mock-fixture-dir：Mock submit → READY 的模拟时长与测试音频目录。
 */
@ConfigurationProperties("app.ai.audio")
public record AppAudioProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String voiceId,
        long mockDelayMs,
        String mockFixtureDir,
        String secretId,
        String secretKey,
        String region,
        String syncOutputDir) {

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
        if (secretId == null) {
            secretId = "";
        }
        if (secretKey == null) {
            secretKey = "";
        }
        if (region == null || region.isBlank()) {
            region = "ap-guangzhou";
        }
        if (syncOutputDir == null || syncOutputDir.isBlank()) {
            syncOutputDir = "./data/tencent-sync";
        }
    }
}
