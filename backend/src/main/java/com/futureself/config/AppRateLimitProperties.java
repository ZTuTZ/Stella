package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流配置（实施文档 §24）：按用户/操作的窗口计数。
 * 默认：创建 Goal 30/时、AI 追问 20/时、生成故事 10/日、重试音频 5/日/故事。
 */
@ConfigurationProperties("app.rate-limit")
public record AppRateLimitProperties(
        int goalCreatePerHour,
        int followupPerHour,
        int storyGeneratePerDay,
        int retryAudioPerDayPerStory) {

    public AppRateLimitProperties {
        if (goalCreatePerHour <= 0) {
            goalCreatePerHour = 30;
        }
        if (followupPerHour <= 0) {
            followupPerHour = 20;
        }
        if (storyGeneratePerDay <= 0) {
            storyGeneratePerDay = 10;
        }
        if (retryAudioPerDayPerStory <= 0) {
            retryAudioPerDayPerStory = 5;
        }
    }
}
