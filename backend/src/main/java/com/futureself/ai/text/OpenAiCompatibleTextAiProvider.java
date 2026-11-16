package com.futureself.ai.text;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.config.AppTextAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI-compatible 文本 AI 实现（默认通用实现，实施文档 §13.2）。
 * baseUrl/apiKey/model 全部来自环境变量配置；不做供应商特例分支。
 * 对响应/错误只记录脱敏摘要（截断、不含 key）。
 */
public class OpenAiCompatibleTextAiProvider implements TextAiProvider {

    public static final String NAME = "openai";

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleTextAiProvider.class);
    private static final int LOG_TRUNCATE = 300;

    private final AppTextAiProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiCompatibleTextAiProvider(AppTextAiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(normalizeBaseUrl(props.baseUrl()))
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .requestFactory(factory)
                .build();
    }

    @Override
    public <T> T generateStructured(TextAiRequest request, Class<T> responseType) {
        long startedAt = System.currentTimeMillis();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", request.systemPrompt()));
        messages.add(new ChatMessage("user", request.userPrompt()));

        var payload = java.util.Map.of(
                "model", props.model(),
                "messages", messages,
                "temperature", request.temperature(),
                "max_tokens", request.maxTokens(),
                "response_format", java.util.Map.of("type", "json_object"));

        String content;
        Integer inputTokens = null;
        Integer outputTokens = null;
        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .header("X-Request-Id", request.requestId() == null ? "" : request.requestId())
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new BusinessException(ErrorCode.TEXT_AI_UNAVAILABLE, "text ai empty response");
            }
            content = response.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new BusinessException(ErrorCode.TEXT_AI_INVALID_RESPONSE, "text ai returned no content");
            }
            inputTokens = response.path("usage").path("prompt_tokens").asInt(0);
            outputTokens = response.path("usage").path("completion_tokens").asInt(0);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("text ai request failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.TEXT_AI_UNAVAILABLE);
        }

        String cleaned = stripCodeFence(content);
        try {
            T result = objectMapper.readValue(cleaned, responseType);
            log.info("text ai ok: op={} latencyMs={} in={} out={}",
                    request.operation(), System.currentTimeMillis() - startedAt, inputTokens, outputTokens);
            // token 数量通过 ThreadLocal 传递给调用方记录（避免接口签名扩散）
            UsageHolder.set(inputTokens, outputTokens);
            return result;
        } catch (JacksonException e) {
            log.warn("text ai invalid json: {}", truncate(cleaned));
            throw new BusinessException(ErrorCode.TEXT_AI_INVALID_RESPONSE);
        }
    }

    @Override
    public String providerName() {
        return NAME;
    }

    /** 最近一次调用的 usage（由调用方读取后清空）；Mock 返回空。 */
    public static final class UsageHolder {
        private static final ThreadLocal<int[]> USAGE = new ThreadLocal<>();

        static void set(Integer inputTokens, Integer outputTokens) {
            USAGE.set(new int[]{
                    inputTokens == null ? -1 : inputTokens,
                    outputTokens == null ? -1 : outputTokens});
        }

        public static int[] poll() {
            int[] usage = USAGE.get();
            USAGE.remove();
            return usage;
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.TEXT_AI_UNAVAILABLE, "AI_TEXT_BASE_URL not configured");
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String stripCodeFence(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            int closing = text.lastIndexOf("```");
            if (closing >= 0) {
                text = text.substring(0, closing);
            }
        }
        return text.trim();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= LOG_TRUNCATE) {
            return value;
        }
        return value.substring(0, LOG_TRUNCATE) + "…";
    }

    private record ChatMessage(String role, String content) {
    }
}
