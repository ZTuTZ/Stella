package com.futureself.ai.audio.provider;

import com.futureself.ai.audio.AiAudioProvider;
import com.futureself.ai.audio.AudioGenerationRequest;
import com.futureself.ai.audio.AudioProviderCapabilities;
import com.futureself.ai.audio.AudioQueryResult;
import com.futureself.ai.audio.AudioSubmitResult;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.config.AppAudioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 腾讯云长文本语音合成 Provider（实施文档 §15.5 / Phase 6）。
 *
 * <p>依据文档：
 * <ul>
 *   <li>CreateTtsTask：异步提交，单次 ≤10 万字符，返回 TaskId（20 QPS）</li>
 *   <li>DescribeTtsTaskStatus：轮询结果，Status 0等待/1执行中/2成功/3失败，
 *       ResultUrl 为 COS 链接（<b>有效期 1 天</b>），ErrorMsg 为失败原因</li>
 *   <li>签名：TC3-HMAC-SHA256，域名 tts.tencentcloudapi.com，Version 2019-08-23</li>
 * </ul>
 *
 * <p>安全：SecretId/SecretKey 仅从配置（环境变量）读取；日志不输出签名与密钥。
 * 结果 URL 会过期 → capabilities.resultUrlMayExpire=true，由业务层 READY 后立即转存。
 */
public class TencentCloudAudioProvider implements AiAudioProvider {

    static final String HOST = "tts.tencentcloudapi.com";
    static final String SERVICE = "tts";
    static final String VERSION = "2019-08-23";
    static final String ENDPOINT = "https://tts.tencentcloudapi.com/";
    static final int MAX_TEXT_CHARS = 100_000;
    static final String PROVIDER_NAME = "tencent";

    private static final Logger log = LoggerFactory.getLogger(TencentCloudAudioProvider.class);

    private final AppAudioProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TencentCloudAudioProvider(AppAudioProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public AudioProviderCapabilities capabilities() {
        // 异步 + 支持回调（我们轮询）+ 支持音色选择/语速 + 结果 URL 会过期（1 天）
        return new AudioProviderCapabilities(true, true, false, true, true, true);
    }

    @Override
    public AudioSubmitResult submit(AudioGenerationRequest request) {
        ensureConfigured();
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.AUDIO_SUBMIT_FAILED, "narration text is empty");
        }
        if (text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Text", text);
        payload.put("VoiceType", resolveVoiceType(request.voiceId()));
        payload.put("Speed", 0);
        payload.put("Volume", 0);
        payload.put("SampleRate", 16000);
        payload.put("Codec", "mp3");
        payload.put("PrimaryLanguage", 1);
        payload.put("ProjectId", 0);
        payload.put("ModelType", 1);

        JsonNode response = call("CreateTtsTask", payload);
        String taskId = parseTaskId(response);
        if (taskId == null) {
            log.warn("tencent tts submit returned no TaskId: requestId={}", requestIdOf(response));
            throw new BusinessException(ErrorCode.AUDIO_SUBMIT_FAILED, "tencent tts: no taskId");
        }
        log.info("tencent tts submitted: taskId={} storyId={} chars={} requestId={}",
                taskId, request.storyId(), text.length(), requestIdOf(response));
        return new AudioSubmitResult(taskId);
    }

    @Override
    public AudioQueryResult query(String providerTaskId) {
        ensureConfigured();
        if (providerTaskId == null || providerTaskId.isBlank()) {
            throw new BusinessException(ErrorCode.AUDIO_PROCESSING_FAILED, "tencent tts: empty taskId");
        }
        JsonNode response = call("DescribeTtsTaskStatus", Map.of("TaskId", providerTaskId));
        return parseStatus(response);
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    // —— 解析（包级可见，便于单测）——

    static String parseTaskId(JsonNode response) {
        String taskId = response.path("Data").path("TaskId").asText("");
        return taskId.isBlank() ? null : taskId;
    }

    /** Status：0 等待 / 1 执行中 / 2 成功 / 3 失败；其余按处理中兜底。 */
    static AudioQueryResult parseStatus(JsonNode response) {
        JsonNode data = response.path("Data");
        int status = data.path("Status").asInt(-1);
        String resultUrl = data.path("ResultUrl").asText("");
        String errorMsg = data.path("ErrorMsg").asText("");
        if (status == 2 && !resultUrl.isBlank()) {
            return new AudioQueryResult(AudioQueryResult.JobStatus.READY, resultUrl, "mp3", null, null, null);
        }
        if (status == 3) {
            return new AudioQueryResult(AudioQueryResult.JobStatus.FAILED, null, null, null,
                    "TENCENT_TTS_TASK_FAILED", errorMsg.isBlank() ? "tencent tts task failed" : errorMsg);
        }
        if (status == 2) {
            // 成功但缺少 URL：视为失败，避免任务悬挂
            return new AudioQueryResult(AudioQueryResult.JobStatus.FAILED, null, null, null,
                    "TENCENT_TTS_TASK_FAILED", "result url missing");
        }
        return new AudioQueryResult(AudioQueryResult.JobStatus.PROCESSING, null, null, null, null, null);
    }

    /** 腾讯云错误码 → 内部错误码（§22 体系）。 */
    static ErrorCode mapErrorCode(String tencentCode, boolean submitStage) {
        if (tencentCode == null || tencentCode.isBlank()) {
            return submitStage ? ErrorCode.AUDIO_SUBMIT_FAILED : ErrorCode.AUDIO_PROCESSING_FAILED;
        }
        if (tencentCode.startsWith("AuthFailure")
                || tencentCode.startsWith("UnsupportedOperation")) {
            // 鉴权/未开通/资源包耗尽/欠费：配置或计费问题，重试无益但保持统一错误码
            return ErrorCode.AUDIO_PROVIDER_NOT_CONFIGURED;
        }
        return submitStage ? ErrorCode.AUDIO_SUBMIT_FAILED : ErrorCode.AUDIO_PROCESSING_FAILED;
    }

    // —— 内部实现 ——

    private void ensureConfigured() {
        if (props.secretId() == null || props.secretId().isBlank()
                || props.secretKey() == null || props.secretKey().isBlank()) {
            throw new BusinessException(ErrorCode.AUDIO_PROVIDER_NOT_CONFIGURED,
                    "tencent tts credentials not configured");
        }
    }

    private int resolveVoiceType(String requestVoiceId) {
        String voice = (requestVoiceId != null && !requestVoiceId.isBlank())
                ? requestVoiceId : props.voiceId();
        if (voice == null || voice.isBlank()) {
            throw new BusinessException(ErrorCode.AUDIO_PROVIDER_NOT_CONFIGURED,
                    "tencent tts voiceId not configured");
        }
        try {
            return Integer.parseInt(voice.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.AUDIO_PROVIDER_NOT_CONFIGURED,
                    "tencent tts voiceId must be numeric");
        }
    }

    /** 调用腾讯云 API 并返回 Response 节点；业务错误转 BusinessException。 */
    private JsonNode call(String action, Map<String, Object> payload) {
        boolean submitStage = "CreateTtsTask".equals(action);
        long startedAt = System.currentTimeMillis();
        try {
            String body = objectMapper.writeValueAsString(payload);
            Map<String, String> headers = TencentCloudSigner.headers(
                    action, VERSION, HOST, SERVICE,
                    props.secretId(), props.secretKey(), body, System.currentTimeMillis() / 1000);

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(builder::header);
            HttpResponse<String> httpResponse = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            JsonNode root = objectMapper.readTree(httpResponse.body());
            JsonNode response = root.path("Response");
            String requestId = requestIdOf(response);
            JsonNode error = response.path("Error");
            if (!error.isMissingNode() && !error.isNull() && error.has("Code")) {
                String code = error.path("Code").asText("");
                String message = error.path("Message").asText("");
                log.warn("tencent tts error: action={} code={} message={} requestId={}",
                        action, code, truncate(message, 200), requestId);
                throw new BusinessException(mapErrorCode(code, submitStage), "tencent tts: " + code);
            }
            log.debug("tencent tts ok: action={} latencyMs={} requestId={}",
                    action, System.currentTimeMillis() - startedAt, requestId);
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("tencent tts request failed: action={} {}", action, e.getClass().getSimpleName());
            throw new BusinessException(
                    submitStage ? ErrorCode.AUDIO_SUBMIT_FAILED : ErrorCode.AUDIO_PROCESSING_FAILED);
        }
    }

    private static String requestIdOf(JsonNode response) {
        return response.path("RequestId").asText("");
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
