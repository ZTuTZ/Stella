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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 腾讯云基础语音合成 Provider（方案 ②：同步 TextToVoice + 150 字分段 + MP3 拼接）。
 *
 * <p>流程（利用既有异步状态机，每 tick 合成一段，避免长时间阻塞 worker）：
 * <ol>
 *   <li>submit：把旁白按句切分为 ≤150 字符的段，返回本地 taskId（不立即合成）</li>
 *   <li>query：每次合成下一段并拼接；全部完成后写入本地文件并返回 READY（file: URL）</li>
 * </ol>
 *
 * <p>状态保存在内存：服务重启后未知 taskId → 返回 FAILED，任务走重试通道重新提交。
 * 结果文件位于 sync-output-dir，由 AudioTransferService 转存到自有存储后仍保留（供审计/重试）。
 */
public class TencentCloudSyncAudioProvider implements AiAudioProvider {

    static final String HOST = "tts.tencentcloudapi.com";
    static final String SERVICE = "tts";
    static final String VERSION = "2019-08-23";
    static final String ENDPOINT = "https://tts.tencentcloudapi.com/";
    static final String PROVIDER_NAME = "tencent-sync";

    private static final Logger log = LoggerFactory.getLogger(TencentCloudSyncAudioProvider.class);
    private static final Duration TEMP_FILE_TTL = Duration.ofDays(1);

    private final AppAudioProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, SyncState> states = new ConcurrentHashMap<>();

    public TencentCloudSyncAudioProvider(AppAudioProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** 每段合成状态（内存态；重启丢失由重试机制兜底）。 */
    private record SyncState(List<String> segments, List<byte[]> parts, int nextIndex, String voiceId) {
        SyncState withNextPart(byte[] part) {
            List<byte[]> newParts = new ArrayList<>(parts);
            newParts.add(part);
            return new SyncState(segments, newParts, nextIndex + 1, voiceId);
        }
    }

    @Override
    public AudioProviderCapabilities capabilities() {
        // 对上层表现为异步（submit 拿 taskId，query 推进），实际内部同步分段合成
        return new AudioProviderCapabilities(true, false, false, true, true, false);
    }

    @Override
    public AudioSubmitResult submit(AudioGenerationRequest request) {
        ensureConfigured();
        String text = request.text() == null ? "" : request.text();
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.AUDIO_SUBMIT_FAILED, "narration text is empty");
        }
        List<String> segments = AudioTextSegmenter.split(text);
        if (segments.isEmpty()) {
            throw new BusinessException(ErrorCode.AUDIO_SUBMIT_FAILED, "narration text is empty");
        }
        String taskId = "tencent-sync-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String voice = resolveVoiceId(request.voiceId());
        states.put(taskId, new SyncState(segments, new ArrayList<>(), 0, voice));
        log.info("tencent sync submitted: taskId={} storyId={} chars={} segments={}",
                taskId, request.storyId(), text.length(), segments.size());
        return new AudioSubmitResult(taskId);
    }

    @Override
    public AudioQueryResult query(String providerTaskId) {
        ensureConfigured();
        SyncState state = states.get(providerTaskId);
        if (state == null) {
            // 重启/状态丢失：让任务走重试通道重新提交
            return new AudioQueryResult(AudioQueryResult.JobStatus.FAILED, null, null, null,
                    "TENCENT_SYNC_STATE_LOST", "sync state lost (service restarted?)");
        }

        if (state.nextIndex() < state.segments().size()) {
            String segment = state.segments().get(state.nextIndex());
            byte[] audio = synthesizeSegment(segment, state.voiceId());
            SyncState updated = state.withNextPart(audio);
            states.put(providerTaskId, updated);
            if (updated.nextIndex() < updated.segments().size()) {
                return new AudioQueryResult(AudioQueryResult.JobStatus.PROCESSING, null, null, null, null, null);
            }
            state = updated;
        }

        byte[] mp3 = Mp3Concatenator.concat(state.parts());
        Path file = writeTempFile(providerTaskId, mp3);
        states.remove(providerTaskId);
        log.info("tencent sync ready: taskId={} segments={} bytes={}", providerTaskId,
                state.segments().size(), mp3.length);
        return new AudioQueryResult(AudioQueryResult.JobStatus.READY, file.toUri().toString(), "mp3", null, null, null);
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    // —— 内部实现 ——

    private byte[] synthesizeSegment(String text, String voiceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Text", text);
        payload.put("SessionId", UUID.randomUUID().toString());
        payload.put("VoiceType", parseVoiceType(voiceId));
        payload.put("Speed", 0);
        payload.put("Volume", 0);
        payload.put("SampleRate", 16000);
        payload.put("Codec", "mp3");
        payload.put("PrimaryLanguage", 1);
        payload.put("ProjectId", 0);
        payload.put("ModelType", 1);

        JsonNode response = call("TextToVoice", payload);
        String base64 = response.path("Audio").asText("");
        if (base64.isBlank()) {
            log.warn("tencent sync segment returned empty audio: requestId={}", response.path("RequestId").asText(""));
            throw new BusinessException(ErrorCode.AUDIO_SUBMIT_FAILED, "tencent tts: empty audio");
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUDIO_SUBMIT_FAILED, "tencent tts: invalid audio base64");
        }
    }

    private JsonNode call(String action, Map<String, Object> payload) {
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
            JsonNode error = response.path("Error");
            if (!error.isMissingNode() && !error.isNull() && error.has("Code")) {
                String code = error.path("Code").asText("");
                String message = error.path("Message").asText("");
                log.warn("tencent tts error: action={} code={} message={} requestId={}",
                        action, code, truncate(message, 200), response.path("RequestId").asText(""));
                throw new BusinessException(
                        TencentCloudAudioProvider.mapErrorCode(code, true), "tencent tts: " + code);
            }
            log.debug("tencent tts ok: action={} latencyMs={} requestId={}",
                    action, System.currentTimeMillis() - startedAt, response.path("RequestId").asText(""));
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("tencent tts request failed: action={} {}", action, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AUDIO_SUBMIT_FAILED);
        }
    }

    private Path writeTempFile(String taskId, byte[] mp3) {
        try {
            Path dir = Path.of(props.syncOutputDir()).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path file = dir.resolve(taskId + ".mp3");
            Files.write(file, mp3);
            cleanupOldFiles(dir);
            return file;
        } catch (IOException e) {
            log.warn("tencent sync temp file write failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AUDIO_STORAGE_FAILED, "sync temp file write failed");
        }
    }

    /** 顺手清理超过 TTL 的临时文件（避免磁盘堆积）。 */
    private void cleanupOldFiles(Path dir) {
        Instant cutoff = Instant.now().minus(TEMP_FILE_TTL);
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".mp3"))
                    .filter(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toInstant().isBefore(cutoff);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // 清理失败不影响主流程
                        }
                    });
        } catch (IOException ignored) {
            // 目录遍历失败不影响主流程
        }
    }

    private void ensureConfigured() {
        if (props.secretId() == null || props.secretId().isBlank()
                || props.secretKey() == null || props.secretKey().isBlank()) {
            throw new BusinessException(ErrorCode.AUDIO_PROVIDER_NOT_CONFIGURED,
                    "tencent tts credentials not configured");
        }
    }

    private String resolveVoiceId(String requestVoiceId) {
        String voice = (requestVoiceId != null && !requestVoiceId.isBlank())
                ? requestVoiceId : props.voiceId();
        if (voice == null || voice.isBlank()) {
            throw new BusinessException(ErrorCode.AUDIO_PROVIDER_NOT_CONFIGURED,
                    "tencent tts voiceId not configured");
        }
        return voice.trim();
    }

    private int parseVoiceType(String voiceId) {
        try {
            return Integer.parseInt(voiceId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.AUDIO_PROVIDER_NOT_CONFIGURED,
                    "tencent tts voiceId must be numeric");
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
