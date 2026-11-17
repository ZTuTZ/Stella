package com.futureself.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 生产配置 fail-fast（H-10）：prod profile 下禁止 mock/local 占位配置与缺失密钥。
 * 只做启动校验，不修改任何运行时行为。
 */
@Component
@Profile("prod")
public class ProductionConfigurationValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionConfigurationValidator.class);

    private final Environment env;

    public ProductionConfigurationValidator(Environment env) {
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> violations = new ArrayList<>();

        if (Boolean.parseBoolean(value("app.wechat.mock-login", "false"))) {
            violations.add("WECHAT_MOCK_LOGIN must be false");
        }
        require("app.wechat.app-id", "WECHAT_APP_ID", violations);
        require("app.wechat.app-secret", "WECHAT_APP_SECRET", violations);

        String textProvider = value("app.ai.text.provider", "mock");
        if ("mock".equalsIgnoreCase(textProvider)) {
            violations.add("AI_TEXT_PROVIDER must not be mock");
        } else {
            require("app.ai.text.base-url", "AI_TEXT_BASE_URL", violations);
            require("app.ai.text.api-key", "AI_TEXT_API_KEY", violations);
            require("app.ai.text.model", "AI_TEXT_MODEL", violations);
        }

        String audioProvider = value("app.ai.audio.provider", "mock");
        if ("mock".equalsIgnoreCase(audioProvider)) {
            violations.add("AI_AUDIO_PROVIDER must not be mock");
        } else if ("tencent".equalsIgnoreCase(audioProvider) || "tencent-sync".equalsIgnoreCase(audioProvider)) {
            require("app.ai.audio.secret-id", "AI_AUDIO_TENCENT_SECRET_ID", violations);
            require("app.ai.audio.secret-key", "AI_AUDIO_TENCENT_SECRET_KEY", violations);
            require("app.ai.audio.voice-id", "AI_AUDIO_VOICE_ID", violations);
        } else if ("openai".equalsIgnoreCase(audioProvider)) {
            require("app.ai.audio.base-url", "AI_AUDIO_BASE_URL", violations);
            require("app.ai.audio.api-key", "AI_AUDIO_API_KEY", violations);
            require("app.ai.audio.voice-id", "AI_AUDIO_VOICE_ID", violations);
        }

        String storageProvider = value("app.storage.provider", "local");
        if ("local".equalsIgnoreCase(storageProvider)) {
            violations.add("STORAGE_PROVIDER must not be local");
        } else if ("oss".equalsIgnoreCase(storageProvider)) {
            require("app.storage.oss.endpoint", "OSS_ENDPOINT", violations);
            require("app.storage.oss.bucket", "OSS_BUCKET", violations);
            require("app.storage.oss.access-key-id", "OSS_ACCESS_KEY_ID", violations);
            require("app.storage.oss.access-key-secret", "OSS_ACCESS_KEY_SECRET", violations);
        }

        if (value("app.admin.bootstrap-password", "").isBlank()) {
            violations.add("ADMIN_BOOTSTRAP_PASSWORD must be set (or provide admin out-of-band)");
        }

        if (!violations.isEmpty()) {
            log.error("production configuration invalid: {}", String.join("; ", violations));
            throw new IllegalStateException("production configuration invalid: " + String.join("; ", violations));
        }
        log.info("production configuration validated: providers text={} audio={} storage={}",
                textProvider, audioProvider, storageProvider);
    }

    private void require(String property, String envName, List<String> violations) {
        if (value(property, "").isBlank()) {
            violations.add(envName + " must be set");
        }
    }

    private String value(String key, String fallback) {
        String v = env.getProperty(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}
