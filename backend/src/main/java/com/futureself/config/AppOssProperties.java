package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置（app.storage.oss，实施文档 §16.3）。
 * AccessKey 只从环境变量/.env 读取，禁止入库/入日志。
 */
@ConfigurationProperties("app.storage.oss")
public record AppOssProperties(
        String endpoint,
        String bucket,
        String accessKeyId,
        String accessKeySecret,
        String publicBaseUrl) {

    public AppOssProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://oss-cn-hangzhou.aliyuncs.com";
        }
        if (bucket == null) {
            bucket = "";
        }
        if (accessKeyId == null) {
            accessKeyId = "";
        }
        if (accessKeySecret == null) {
            accessKeySecret = "";
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = "";
        }
    }
}
