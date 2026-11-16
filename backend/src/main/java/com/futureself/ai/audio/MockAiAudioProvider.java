package com.futureself.ai.audio;

import com.futureself.config.AppAudioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock 音频 Provider（实施文档 §15.4）：
 * submit 返回模拟 providerTaskId；经过可配置延时后 query 返回 READY；
 * READY 携带项目内测试音频 fixture 的 file: URL，由转存服务写入 StorageService。
 * 内存态在重启后丢失：未知 taskId 直接返回 READY（Mock 语义，便于联调）。
 */
public class MockAiAudioProvider implements AiAudioProvider {

    private static final Logger log = LoggerFactory.getLogger(MockAiAudioProvider.class);

    private final AppAudioProperties props;
    private final Map<String, Instant> submittedAt = new ConcurrentHashMap<>();
    private final String fixtureUrl;

    public MockAiAudioProvider(AppAudioProperties props) {
        this.props = props;
        this.fixtureUrl = Path.of(props.mockFixtureDir()).toAbsolutePath().normalize()
                .resolve("mock-narration.wav").toUri().toString();
    }

    @Override
    public AudioProviderCapabilities capabilities() {
        // Mock 模拟异步供应商；不支持 NARRATION_MIX / 音色 / 语速选择；URL 不存在过期问题
        return new AudioProviderCapabilities(true, false, false, false, false, false);
    }

    @Override
    public AudioSubmitResult submit(AudioGenerationRequest request) {
        String providerTaskId = "mock-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        submittedAt.put(providerTaskId, Instant.now());
        log.info("mock audio submitted: providerTaskId={} storyId={} mode={}",
                providerTaskId, request.storyId(), request.mode());
        return new AudioSubmitResult(providerTaskId);
    }

    @Override
    public AudioQueryResult query(String providerTaskId) {
        Instant at = submittedAt.get(providerTaskId);
        boolean ready = at == null
                || Instant.now().toEpochMilli() - at.toEpochMilli() >= props.mockDelayMs();
        if (ready) {
            return new AudioQueryResult(AudioQueryResult.JobStatus.READY, fixtureUrl, "wav", 2000L, null, null);
        }
        return new AudioQueryResult(AudioQueryResult.JobStatus.PROCESSING, null, null, null, null, null);
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
