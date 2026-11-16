package com.futureself.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据保留配置（实施文档 §25.2）：已删除故事音频的物理清理保留期。
 */
@ConfigurationProperties("app.retention")
public record AppRetentionProperties(int deletedAudioDays) {

    public AppRetentionProperties {
        if (deletedAudioDays <= 0) {
            deletedAudioDays = 30;
        }
    }
}
