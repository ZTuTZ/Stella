package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文本 AI 配置（app.ai.text，实施文档 §13.2/§21）。
 * provider: mock | openai；api-key 只从环境变量/.env 读取。
 */
@ConfigurationProperties("app.ai.text")
public record AppTextAiProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds) {

    public AppTextAiProperties {
        if (provider == null || provider.isBlank()) {
            provider = "mock";
        }
        if (baseUrl == null) {
            baseUrl = "";
        }
        if (apiKey == null) {
            apiKey = "";
        }
        if (model == null) {
            model = "";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 60;
        }
    }
}
