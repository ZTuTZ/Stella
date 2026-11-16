package com.futureself.ai.audio;

import com.futureself.config.AppAudioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 音频 Provider 装配（实施文档 §15）。
 * 当前仅有 mock；真实供应商文档到位后在此追加 @ConditionalOnProperty Bean。
 * 未知 provider 值会导致无 Bean 可装配 → 启动失败（明确的配置错误，符合 §28 Phase 6 验收）。
 */
@Configuration
public class AudioProviderConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.audio.provider", havingValue = "mock", matchIfMissing = true)
    public AiAudioProvider mockAiAudioProvider(AppAudioProperties props) {
        ensureMockFixture(props);
        return new MockAiAudioProvider(props);
    }

    /** 启动时确保 Mock fixture（2 秒静音 WAV）存在，供转存与播放联调。 */
    static void ensureMockFixture(AppAudioProperties props) {
        try {
            Path dir = Path.of(props.mockFixtureDir()).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path file = dir.resolve("mock-narration.wav");
            if (!Files.exists(file)) {
                Files.write(file, silentWav(2, 8000));
            }
        } catch (IOException e) {
            LoggerFactory.getLogger(AudioProviderConfig.class)
                    .warn("mock audio fixture init failed: {}", e.getClass().getSimpleName());
        }
    }

    /** 生成最小合法 WAV（PCM 16bit 单声道静音）。 */
    static byte[] silentWav(int seconds, int sampleRate) {
        int dataLen = seconds * sampleRate * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()).putInt(36 + dataLen).put("WAVE".getBytes());
        buffer.put("fmt ".getBytes()).putInt(16).putShort((short) 1) // PCM
                .putShort((short) 1).putInt(sampleRate).putInt(sampleRate * 2)
                .putShort((short) 2).putShort((short) 16);
        buffer.put("data".getBytes()).putInt(dataLen);
        return buffer.array();
    }
}
