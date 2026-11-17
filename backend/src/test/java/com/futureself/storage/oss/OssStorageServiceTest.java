package com.futureself.storage.oss;

import com.futureself.common.error.BusinessException;
import com.futureself.config.AppOssProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OSS 存储：签名结构/公网 URL/key 校验/未配置快速失败（不发起网络请求）。
 */
class OssStorageServiceTest {

    private OssStorageService service(String publicBaseUrl) {
        return new OssStorageService(new AppOssProperties(
                "https://oss-cn-hangzhou.aliyuncs.com", "heyi2",
                "test-access-key", "test-secret", publicBaseUrl));
    }

    @Test
    void buildsOssV1AuthorizationHeader() {
        OssStorageService svc = service("");
        String auth = svc.sign("PUT", "md5value", "audio/mpeg",
                "Tue, 17 Nov 2026 10:00:00 GMT", "audio/8/5/a.mp3");

        assertThat(auth)
                .startsWith("OSS test-access-key:")
                .matches("OSS test-access-key:[A-Za-z0-9+/=]+$");
        assertThat(auth).doesNotContain("test-secret");
    }

    @Test
    void signatureIsDeterministicAndVerbSensitive() {
        OssStorageService svc = service("");
        String date = "Tue, 17 Nov 2026 10:00:00 GMT";
        String put = svc.sign("PUT", "m", "audio/mpeg", date, "audio/1.mp3");
        String head = svc.sign("HEAD", "", "", date, "audio/1.mp3");
        assertThat(put).isNotEqualTo(head);
        assertThat(svc.sign("PUT", "m", "audio/mpeg", date, "audio/1.mp3")).isEqualTo(put);
    }

    @Test
    void publicUrlUsesBucketHostOrConfiguredBase() {
        assertThat(service("").publicUrl("audio/1/a.mp3"))
                .isEqualTo("https://heyi2.oss-cn-hangzhou.aliyuncs.com/audio/1/a.mp3");
        assertThat(service("https://cdn.example.com/media/").publicUrl("audio/1/a.mp3"))
                .isEqualTo("https://cdn.example.com/media/audio/1/a.mp3");
    }

    @Test
    void rejectsInvalidKeys() {
        OssStorageService svc = service("");
        assertThatThrownBy(() -> svc.save("../evil.mp3", new ByteArrayInputStream(new byte[0]), "audio/mpeg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.save("a//b.mp3", new ByteArrayInputStream(new byte[0]), "audio/mpeg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.save("/abs.mp3", new ByteArrayInputStream(new byte[0]), "audio/mpeg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unconfiguredBucketFailsFast() {
        OssStorageService svc = new OssStorageService(new AppOssProperties(
                "https://oss-cn-hangzhou.aliyuncs.com", "", "", "", ""));
        assertThatThrownBy(() -> svc.save("audio/1/a.mp3",
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), "audio/mpeg"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode().name())
                .isEqualTo("AUDIO_STORAGE_FAILED");
    }
}
