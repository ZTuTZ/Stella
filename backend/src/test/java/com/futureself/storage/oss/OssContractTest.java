package com.futureself.storage.oss;

import com.futureself.config.AppOssProperties;
import com.futureself.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阿里云 OSS 契约测试：默认跳过，需 `OSS_CONTRACT_TEST=true` 且环境变量提供凭据时手动执行。
 *
 * <pre>OSS_CONTRACT_TEST=true mvn test -Dtest=OssContractTest</pre>
 */
@EnabledIfEnvironmentVariable(named = "OSS_CONTRACT_TEST", matches = "true")
class OssContractTest {

    @Test
    void uploadHeadAndDelete() throws Exception {
        String ak = System.getenv("OSS_ACCESS_KEY_ID");
        String sk = System.getenv("OSS_ACCESS_KEY_SECRET");
        String bucket = System.getenv().getOrDefault("OSS_BUCKET", "heyi2");
        String endpoint = System.getenv().getOrDefault("OSS_ENDPOINT", "https://oss-cn-hangzhou.aliyuncs.com");
        assertThat(ak).as("OSS_ACCESS_KEY_ID").isNotBlank();
        assertThat(sk).as("OSS_ACCESS_KEY_SECRET").isNotBlank();

        OssStorageService storage = new OssStorageService(
                new AppOssProperties(endpoint, bucket, ak, sk, ""));
        String key = "test/contract-" + UUID.randomUUID().toString().replace("-", "") + ".txt";
        byte[] payload = "hello oss".getBytes(StandardCharsets.UTF_8);

        StoredObject saved = storage.save(key, new ByteArrayInputStream(payload), "text/plain");
        assertThat(saved.url()).contains(bucket);
        assertThat(saved.sizeBytes()).isEqualTo(payload.length);

        Optional<StoredObject> head = storage.get(key);
        assertThat(head).isPresent();
        assertThat(head.get().sizeBytes()).isEqualTo(payload.length);

        // 公网可达性（bucket 若为私有则 403，仅打印不失败）
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpResponse<Void> publicResp = client.send(
                HttpRequest.newBuilder(URI.create(saved.url())).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        System.out.println("public GET status=" + publicResp.statusCode() + " url=" + saved.url());

        storage.delete(key);
        assertThat(storage.get(key)).isEmpty();
        System.out.println("OSS contract ok: " + key);
    }
}
