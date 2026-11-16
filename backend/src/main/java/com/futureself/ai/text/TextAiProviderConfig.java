package com.futureself.ai.text;

import com.futureself.config.AppTextAiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 文本 AI Provider 装配（实施文档 §13.2）。
 * app.ai.text.provider = mock（默认，无 key 可完整演示）| openai（OpenAI-compatible 通用实现）。
 * 切换 Provider 不改业务代码。
 */
@Configuration
public class TextAiProviderConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.text.provider", havingValue = "mock", matchIfMissing = true)
    public TextAiProvider mockTextAiProvider(ObjectMapper objectMapper) {
        return new MockTextAiProvider(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.text.provider", havingValue = "openai")
    public TextAiProvider openAiCompatibleTextAiProvider(AppTextAiProperties props, ObjectMapper objectMapper) {
        return new OpenAiCompatibleTextAiProvider(props, objectMapper);
    }
}
