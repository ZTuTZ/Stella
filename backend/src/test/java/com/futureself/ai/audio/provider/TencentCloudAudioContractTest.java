package com.futureself.ai.audio.provider;

import com.futureself.ai.audio.AudioGenerationRequest;
import com.futureself.ai.audio.AudioMode;
import com.futureself.ai.audio.AudioQueryResult;
import com.futureself.ai.audio.AudioSubmitResult;
import com.futureself.config.AppAudioProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实契约测试（腾讯云长文本 TTS）：
 * 默认跳过 —— 需要 `AI_AUDIO_TENCENT_CONTRACT=true` 且配置密钥/资源包时手动执行：
 *
 * <pre>AI_AUDIO_TENCENT_CONTRACT=true mvn test -Dtest=TencentCloudAudioContractTest</pre>
 *
 * 说明（与 §15.5 第 7 条的差异）：本项目密钥存在但资源包可能未购买；为避免 CI 每次扣费/因欠费失败，
 * 改用显式开关而非“有 key 即跑”。
 */
@EnabledIfEnvironmentVariable(named = "AI_AUDIO_TENCENT_CONTRACT", matches = "true")
class TencentCloudAudioContractTest {

    @Test
    void submitAndPollUntilReady() throws Exception {
        String secretId = System.getenv("AI_AUDIO_TENCENT_SECRET_ID");
        String secretKey = System.getenv("AI_AUDIO_TENCENT_SECRET_KEY");
        String voiceId = System.getenv().getOrDefault("AI_AUDIO_VOICE_ID", "301001");
        assertThat(secretId).as("AI_AUDIO_TENCENT_SECRET_ID").isNotBlank();
        assertThat(secretKey).as("AI_AUDIO_TENCENT_SECRET_KEY").isNotBlank();

        AppAudioProperties props = new AppAudioProperties(
                "tencent", "", "", voiceId, 0, "./data/mock-audio", secretId, secretKey, "ap-guangzhou", "target/test-media/tencent-sync", "");
        TencentCloudAudioProvider provider = new TencentCloudAudioProvider(props, new ObjectMapper());

        AudioSubmitResult submitted = provider.submit(new AudioGenerationRequest(
                "contract-" + System.currentTimeMillis(), 0L, "你好，这是一个契约测试。",
                null, null, null, AudioMode.NARRATION, null));
        assertThat(submitted.providerTaskId()).isNotBlank();

        AudioQueryResult.JobStatus status = AudioQueryResult.JobStatus.PROCESSING;
        String url = null;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(2000);
            AudioQueryResult result = provider.query(submitted.providerTaskId());
            status = result.status();
            url = result.url();
            if (status != AudioQueryResult.JobStatus.PROCESSING) {
                break;
            }
        }
        assertThat(status).as("task should reach READY").isEqualTo(AudioQueryResult.JobStatus.READY);
        assertThat(url).isNotBlank();
    }
}
