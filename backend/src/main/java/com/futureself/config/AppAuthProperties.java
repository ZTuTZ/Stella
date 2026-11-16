package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证配置（app.auth，实施文档 §21）。Phase 2 使用。
 */
@ConfigurationProperties("app.auth")
public record AppAuthProperties(long tokenTtlSeconds) {

    public AppAuthProperties {
        if (tokenTtlSeconds <= 0) {
            tokenTtlSeconds = 7200;
        }
    }
}
