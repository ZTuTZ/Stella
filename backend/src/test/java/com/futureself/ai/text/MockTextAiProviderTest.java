package com.futureself.ai.text;

import com.futureself.common.error.BusinessException;
import com.futureself.goal.FollowupService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mock Provider：结构正确、与真实 provider 相同的解析路径。
 */
class MockTextAiProviderTest {

    private final MockTextAiProvider provider = new MockTextAiProvider(new ObjectMapper());

    @Test
    void followupReturnsParseableQuestions() {
        FollowupService.FollowupQuestionsResult result = provider.generateStructured(
                new TextAiRequest("FOLLOWUP", "sys", "user", null, null, null, "req-test"),
                FollowupService.FollowupQuestionsResult.class);
        assertThat(result.questions()).hasSizeBetween(0, 3);
        assertThat(result.questions().get(0).id()).isEqualTo("q1");
        assertThat(result.questions().get(0).question()).isNotBlank();
    }

    @Test
    void unknownOperationRejected() {
        assertThatThrownBy(() -> provider.generateStructured(
                new TextAiRequest("NOPE", "sys", "user", null, null, null, "req-test"), Object.class))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode().name())
                .isEqualTo("TEXT_AI_INVALID_RESPONSE");
    }

    @Test
    void providerName() {
        assertThat(provider.providerName()).isEqualTo("mock");
    }
}
