package com.futureself.ai.audio.provider;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC3-HMAC-SHA256 签名（腾讯云 API 3.0）：
 * 只验证结构与确定性；真实连通性由契约测试（需资源包）覆盖。
 */
class TencentCloudSignerTest {

    private static final String SECRET_ID = "AKIDtestSecretId123";
    private static final String SECRET_KEY = "testSecretKey456";

    private Map<String, String> sign(String action, String payload, long ts) {
        return TencentCloudSigner.headers(action, "2019-08-23", "tts.tencentcloudapi.com", "tts",
                SECRET_ID, SECRET_KEY, payload, ts);
    }

    @Test
    void producesTc3AuthorizationHeader() {
        Map<String, String> headers = sign("CreateTtsTask", "{\"Text\":\"你好\"}", 1700000000L);

        assertThat(headers.get("Authorization"))
                .startsWith("TC3-HMAC-SHA256 Credential=" + SECRET_ID + "/")
                .contains("/tts/tc3_request")
                .contains("SignedHeaders=content-type;host;x-tc-action")
                .matches(".*Signature=[0-9a-f]{64}$");
        assertThat(headers.get("Content-Type")).isEqualTo("application/json; charset=utf-8");
        assertThat(headers.get("X-TC-Action")).isEqualTo("CreateTtsTask");
        assertThat(headers.get("X-TC-Version")).isEqualTo("2019-08-23");
        assertThat(headers.get("X-TC-Timestamp")).isEqualTo("1700000000");
        assertThat(headers).doesNotContainKeys("Host");
    }

    @Test
    void doesNotLeakSecretKeyAnywhere() {
        Map<String, String> headers = sign("DescribeTtsTaskStatus", "{\"TaskId\":\"gz-1\"}", 1700000000L);
        assertThat(headers.values()).noneMatch(v -> v.contains(SECRET_KEY));
    }

    @Test
    void signatureIsDeterministicAndPayloadSensitive() {
        String payload = "{\"Text\":\"你好\"}";
        String auth1 = sign("CreateTtsTask", payload, 1700000000L).get("Authorization");
        String auth2 = sign("CreateTtsTask", payload, 1700000000L).get("Authorization");
        String auth3 = sign("CreateTtsTask", payload + " ", 1700000000L).get("Authorization");
        String auth4 = sign("CreateTtsTask", payload, 1700000001L).get("Authorization");

        assertThat(auth2).isEqualTo(auth1);
        assertThat(auth3).isNotEqualTo(auth1);
        assertThat(auth4).isNotEqualTo(auth1);
    }
}
