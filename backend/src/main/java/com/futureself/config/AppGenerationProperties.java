package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 生成任务配置（app.generation，实施文档 §21）。
 * retryBackoffBaseMs：RETRY_WAIT 退避基数，实际间隔 = base * 2^(已重试次数-1)。
 */
@ConfigurationProperties("app.generation")
public record AppGenerationProperties(long workerDelayMs, int maxRetryCount, long retryBackoffBaseMs) {

    public AppGenerationProperties {
        if (workerDelayMs <= 0) {
            workerDelayMs = 1500;
        }
        if (maxRetryCount <= 0) {
            maxRetryCount = 3;
        }
        // 0 允许（测试中立即重试）；仅负数回退默认
        if (retryBackoffBaseMs < 0) {
            retryBackoffBaseMs = 5000;
        }
    }
}
