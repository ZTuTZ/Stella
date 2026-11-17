package com.futureself.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H-10：prod profile 下 mock/local 配置必须启动失败。
 */
class ProductionConfigurationValidatorTest {

    private MockEnvironment baseEnv() {
        return new MockEnvironment()
                .withProperty("app.wechat.mock-login", "false")
                .withProperty("app.wechat.app-id", "wxid")
                .withProperty("app.wechat.app-secret", "secret")
                .withProperty("app.ai.text.provider", "openai")
                .withProperty("app.ai.text.base-url", "https://api.deepseek.com/v1")
                .withProperty("app.ai.text.api-key", "k")
                .withProperty("app.ai.text.model", "deepseek-chat")
                .withProperty("app.ai.audio.provider", "tencent-sync")
                .withProperty("app.ai.audio.secret-id", "sid")
                .withProperty("app.ai.audio.secret-key", "skey")
                .withProperty("app.ai.audio.voice-id", "501004")
                .withProperty("app.storage.provider", "oss")
                .withProperty("app.storage.oss.endpoint", "https://oss-cn-hangzhou.aliyuncs.com")
                .withProperty("app.storage.oss.bucket", "b")
                .withProperty("app.storage.oss.access-key-id", "ak")
                .withProperty("app.storage.oss.access-key-secret", "sk")
                .withProperty("app.admin.bootstrap-password", "pw");
    }

    @Test
    void mockTextProviderFailsFast() {
        MockEnvironment env = baseEnv().withProperty("app.ai.text.provider", "mock");
        assertThatThrownBy(() -> new ProductionConfigurationValidator(env).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_TEXT_PROVIDER");
    }

    @Test
    void localStorageFailsFast() {
        MockEnvironment env = baseEnv().withProperty("app.storage.provider", "local");
        assertThatThrownBy(() -> new ProductionConfigurationValidator(env).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STORAGE_PROVIDER");
    }

    @Test
    void fullProductionConfigPasses() {
        assertThatCode(() -> new ProductionConfigurationValidator(baseEnv()).run(null)).doesNotThrowAnyException();
    }
}
