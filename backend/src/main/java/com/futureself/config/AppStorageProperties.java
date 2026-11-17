package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存储配置（app.storage，实施文档 §16/§21）。
 * dev: provider=local（本地文件 + 受控 /media 路由）；prod: provider=oss/cos/s3。
 */
@ConfigurationProperties("app.storage")
public record AppStorageProperties(String provider, String localDir, String publicBaseUrl,
                                  int readUrlTtlMinutes) {

    public AppStorageProperties {
        if (provider == null || provider.isBlank()) {
            provider = "local";
        }
        if (localDir == null || localDir.isBlank()) {
            localDir = "./data/media";
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = "/media";
        }
        if (readUrlTtlMinutes <= 0) {
            readUrlTtlMinutes = 15;
        }
    }
}
