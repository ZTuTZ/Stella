package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 生成任务配置（app.generation，实施文档 §21）。Phase 4 worker 使用。
 */
@ConfigurationProperties("app.generation")
public record AppGenerationProperties(long workerDelayMs, int maxRetryCount) {

    public AppGenerationProperties {
        if (workerDelayMs <= 0) {
            workerDelayMs = 1500;
        }
        if (maxRetryCount <= 0) {
            maxRetryCount = 3;
        }
    }
}
