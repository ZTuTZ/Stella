package com.futureself.ai.text;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import tools.jackson.databind.ObjectMapper;

/**
 * Mock 文本 AI（实施文档 §13.2/§27.4）：
 * 本地无 key 时返回固定但结构正确的数据；走真实业务与数据库，仅替换模型响应。
 * 反序列化与真实 provider 相同路径，保证上层代码零差异。
 */
public class MockTextAiProvider implements TextAiProvider {

    public static final String NAME = "mock";

    private final ObjectMapper objectMapper;

    public MockTextAiProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T generateStructured(TextAiRequest request, Class<T> responseType) {
        String json = mockJsonFor(request.operation());
        if (json == null) {
            throw new BusinessException(ErrorCode.TEXT_AI_INVALID_RESPONSE,
                    "mock provider does not support operation: " + request.operation());
        }
        try {
            return objectMapper.readValue(json, responseType);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.TEXT_AI_INVALID_RESPONSE, "mock response not parseable");
        }
    }

    @Override
    public String providerName() {
        return NAME;
    }

    private String mockJsonFor(String operation) {
        return switch (operation == null ? "" : operation) {
            // §14.1：0~3 个问题；Mock 固定返回 2 个结构正确的问题
            case "FOLLOWUP" -> """
                    {"questions":[
                      {"id":"q1","question":"当这个目标实现后，你最希望自己每天早晨在哪里醒来？","reason":"把抽象目标落到具体生活场景","optional":true},
                      {"id":"q2","question":"实现它的过程中，你希望身边的人最先注意到你的哪个变化？","reason":"引入重要人物增强真实感","optional":true}
                    ]}
                    """;
            // §14.2 故事结构；Phase 4 使用
            case "STORY" -> """
                    {"title":"工作室的一个普通早晨",
                     "summary":"你从自己的家里醒来，开始一段已经稳定下来的生活。",
                     "content":"清晨的光落在桌面上。你像往常一样醒来，房间里安静而熟悉……（Mock 故事正文，用于本地演示与联调。）",
                     "audioDirection":{"mood":"warm_calm","pace":"slow","notes":"自然、亲密、避免广告腔"}}
                    """;
            // §14.3：6 条，第一人称
            case "AFFIRMATION" -> """
                    {"items":[
                      "我正在把自己的能力变成越来越稳定的选择。",
                      "我值得拥有从容而有秩序的一天。",
                      "我正在靠近自己真正想要的生活。",
                      "我允许自己慢慢来，但不停下来。",
                      "我值得被支持和看见。",
                      "我正在成为未来的自己。"
                    ]}
                    """;
            default -> null;
        };
    }
}
