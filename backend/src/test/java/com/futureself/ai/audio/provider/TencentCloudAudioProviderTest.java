package com.futureself.ai.audio.provider;

import com.futureself.ai.audio.AudioQueryResult;
import com.futureself.common.error.ErrorCode;
import com.futureself.config.AppAudioProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 腾讯云长文本 Provider：响应解析与错误码映射（不发起网络请求）。
 */
class TencentCloudAudioProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode tree(String json) {
        return objectMapper.readTree(json);
    }

    @Test
    void parsesTaskId() {
        JsonNode response = tree("{\"RequestId\":\"r1\",\"Data\":{\"TaskId\":\"gz-abc-123\"}}");
        assertThat(TencentCloudAudioProvider.parseTaskId(response)).isEqualTo("gz-abc-123");
        assertThat(TencentCloudAudioProvider.parseTaskId(tree("{\"RequestId\":\"r1\",\"Data\":{}}"))).isNull();
    }

    @Test
    void mapsStatusWaitingAndDoingToProcessing() {
        assertThat(TencentCloudAudioProvider.parseStatus(
                tree("{\"Data\":{\"Status\":0,\"StatusStr\":\"waiting\"}}")))
                .extracting(AudioQueryResult::status).isEqualTo(AudioQueryResult.JobStatus.PROCESSING);
        assertThat(TencentCloudAudioProvider.parseStatus(
                tree("{\"Data\":{\"Status\":1,\"StatusStr\":\"doing\"}}")))
                .extracting(AudioQueryResult::status).isEqualTo(AudioQueryResult.JobStatus.PROCESSING);
    }

    @Test
    void mapsSuccessToReadyWithUrl() {
        AudioQueryResult result = TencentCloudAudioProvider.parseStatus(
                tree("{\"Data\":{\"Status\":2,\"StatusStr\":\"success\","
                        + "\"ResultUrl\":\"https://cos.example.com/a.mp3\"}}"));
        assertThat(result.status()).isEqualTo(AudioQueryResult.JobStatus.READY);
        assertThat(result.url()).isEqualTo("https://cos.example.com/a.mp3");
        assertThat(result.format()).isEqualTo("mp3");
    }

    @Test
    void mapsFailureToFailedWithErrorMsg() {
        AudioQueryResult result = TencentCloudAudioProvider.parseStatus(
                tree("{\"Data\":{\"Status\":3,\"StatusStr\":\"failed\",\"ErrorMsg\":\"internal error\"}}"));
        assertThat(result.status()).isEqualTo(AudioQueryResult.JobStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("TENCENT_TTS_TASK_FAILED");
        assertThat(result.errorMessage()).isEqualTo("internal error");
    }

    @Test
    void successWithoutUrlTreatedAsFailed() {
        AudioQueryResult result = TencentCloudAudioProvider.parseStatus(
                tree("{\"Data\":{\"Status\":2,\"ResultUrl\":\"\"}}"));
        assertThat(result.status()).isEqualTo(AudioQueryResult.JobStatus.FAILED);
    }

    @Test
    void mapsTencentErrorCodes() {
        assertThat(TencentCloudAudioProvider.mapErrorCode("AuthFailure.InvalidAuthorization", true))
                .isEqualTo(ErrorCode.AUDIO_PROVIDER_NOT_CONFIGURED);
        assertThat(TencentCloudAudioProvider.mapErrorCode("UnsupportedOperation.PkgExhausted", true))
                .isEqualTo(ErrorCode.AUDIO_PROVIDER_NOT_CONFIGURED);
        assertThat(TencentCloudAudioProvider.mapErrorCode("InvalidParameterValue.TextTooLong", true))
                .isEqualTo(ErrorCode.AUDIO_SUBMIT_FAILED);
        assertThat(TencentCloudAudioProvider.mapErrorCode("LimitExceeded.AccessLimit", false))
                .isEqualTo(ErrorCode.AUDIO_PROCESSING_FAILED);
        assertThat(TencentCloudAudioProvider.mapErrorCode("InternalError.InternalError", false))
                .isEqualTo(ErrorCode.AUDIO_PROCESSING_FAILED);
        assertThat(TencentCloudAudioProvider.mapErrorCode(null, true))
                .isEqualTo(ErrorCode.AUDIO_SUBMIT_FAILED);
    }

    @Test
    void submitWithoutCredentialsFailsFast() {
        AppAudioProperties props = new AppAudioProperties(
                "tencent", "", "", "301001", 0, "target/test-media/mock-audio", "", "", "ap-guangzhou", "target/test-media/tencent-sync");
        TencentCloudAudioProvider provider = new TencentCloudAudioProvider(props, objectMapper);

        assertThatThrownBy(() -> provider.submit(new com.futureself.ai.audio.AudioGenerationRequest(
                "req-1", 1L, "你好", null, null, null, com.futureself.ai.audio.AudioMode.NARRATION, null)))
                .isInstanceOf(com.futureself.common.error.BusinessException.class)
                .extracting(e -> ((com.futureself.common.error.BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AUDIO_PROVIDER_NOT_CONFIGURED);
    }
}
