package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理后台 bootstrap 配置（实施文档附录 A）。
 * bootstrap-password 为空则跳过创建（并打 warn）；仅本地/内测使用，生产必须更换。
 */
@ConfigurationProperties("app.admin")
public record AppAdminProperties(String bootstrapUsername, String bootstrapPassword) {

    public AppAdminProperties {
        if (bootstrapUsername == null || bootstrapUsername.isBlank()) {
            bootstrapUsername = "admin";
        }
        if (bootstrapPassword == null) {
            bootstrapPassword = "";
        }
    }
}
