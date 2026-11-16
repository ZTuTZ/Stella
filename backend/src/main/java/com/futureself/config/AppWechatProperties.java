package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微信小程序配置（app.wechat，实施文档 §18.2）。
 * app-secret 只从环境变量/.env 读取，禁止写入 Git、日志或响应。
 */
@ConfigurationProperties("app.wechat")
public record AppWechatProperties(
        String appId,
        String appSecret,
        boolean mockLogin,
        String apiBaseUrl,
        long timeoutMs) {

    public AppWechatProperties {
        if (appId == null) {
            appId = "";
        }
        if (appSecret == null) {
            appSecret = "";
        }
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://api.weixin.qq.com";
        }
        if (timeoutMs <= 0) {
            timeoutMs = 5000;
        }
    }
}
