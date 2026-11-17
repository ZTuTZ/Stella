package com.futureself.storage.oss;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.config.AppOssProperties;
import com.futureself.storage.StorageService;
import com.futureself.storage.StoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 阿里云 OSS 存储实现（实施文档 §16.3）：纯 REST + OSS V1 签名，不引入 SDK。
 * 业务层只依赖 {@link StorageService}，切换 provider 即可，无代码改动。
 *
 * <p>签名算法（OSS V1）：
 * <pre>
 * StringToSign = VERB + "\n" + Content-MD5 + "\n" + Content-Type + "\n" + Date
 *                + "\n" + CanonicalizedOSSHeaders + CanonicalizedResource
 * Signature    = Base64(HMAC-SHA1(AccessKeySecret, StringToSign))
 * Authorization: "OSS " + AccessKeyId + ":" + Signature
 * </pre>
 * 不发送任何 x-oss-* 头（CanonicalizedOSSHeaders 为空）。
 */
@Service
@Primary
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "oss")
public class OssStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageService.class);
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{2,499}");
    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                    .withZone(ZoneOffset.UTC);
    private static final int TIMEOUT_SECONDS = 60;

    private final AppOssProperties props;
    private final String endpointBase;
    private final String host;
    private final HttpClient httpClient;

    public OssStorageService(AppOssProperties props) {
        this.props = props;
        this.endpointBase = normalizeEndpoint(props.endpoint());
        this.host = URI.create(endpointBase).getHost();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public StoredObject save(String key, InputStream input, String contentType) {
        validateKey(key);
        ensureConfigured();
        String type = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        byte[] bytes;
        try {
            bytes = input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("oss upload read failed: " + key, e);
        }
        String contentMd5 = base64Md5(bytes);
        String date = HTTP_DATE.format(Instant.now());
        String authorization = sign("PUT", contentMd5, type, date, key);

        HttpRequest request = HttpRequest.newBuilder(objectUri(key))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Date", date)
                .header("Content-Type", type)
                .header("Content-MD5", contentMd5)
                .header("Authorization", authorization)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> response = send(request, key);
        if (response.statusCode() >= 300) {
            log.warn("oss put failed: status={} key={} requestId={}",
                    response.statusCode(), key, response.headers().firstValue("x-oss-request-id").orElse(""));
            throw new IllegalStateException("oss put failed: " + response.statusCode());
        }
        log.info("oss uploaded: key={} size={} contentType={}", key, bytes.length, type);
        return new StoredObject(key, publicUrl(key), type, bytes.length);
    }

    @Override
    public Optional<StoredObject> get(String key) {
        validateKey(key);
        ensureConfigured();
        String date = HTTP_DATE.format(Instant.now());
        HttpRequest request = HttpRequest.newBuilder(objectUri(key))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Date", date)
                .header("Authorization", sign("HEAD", "", "", date, key))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = send(request, key);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("oss head failed: " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse(null);
        long size = response.headers().firstValue("Content-Length")
                .map(Long::parseLong).orElse(-1L);
        return Optional.of(new StoredObject(key, publicUrl(key), contentType, size));
    }

    @Override
    public void delete(String key) {
        validateKey(key);
        ensureConfigured();
        String date = HTTP_DATE.format(Instant.now());
        HttpRequest request = HttpRequest.newBuilder(objectUri(key))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Date", date)
                .header("Authorization", sign("DELETE", "", "", date, key))
                .DELETE()
                .build();
        HttpResponse<String> response = send(request, key);
        if (response.statusCode() >= 300 && response.statusCode() != 404) {
            throw new IllegalStateException("oss delete failed: " + response.statusCode());
        }
    }

    // —— 内部实现 ——

    private HttpResponse<String> send(HttpRequest request, String key) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("oss request failed: key={} {}", key, e.getClass().getSimpleName());
            throw new IllegalStateException("oss request failed", e);
        }
    }

    /**
     * 三级域名（virtual-hosted style）对象地址：https://{bucket}.{host}/{key}。
     * 该账号禁止 path-style（SecondLevelDomainForbidden），故必须走 bucket 子域名；
     * 实测该场景下 OSS V1 签名资源仍为 /{bucket}/{key}（OSS 回显 StringToSign 可验证）。
     */
    private URI objectUri(String key) {
        String scheme = endpointBase.substring(0, endpointBase.indexOf("://"));
        return URI.create(scheme + "://" + props.bucket() + "." + host + "/" + key);
    }

    /** 对象公网地址：优先 publicBaseUrl，否则 https://{bucket}.{host}/{key}。 */
    public String publicUrl(String key) {
        String base = props.publicBaseUrl();
        if (base != null && !base.isBlank()) {
            String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            return trimmed + "/" + key;
        }
        return "https://" + props.bucket() + "." + host + "/" + key;
    }

    /** 生成 OSS V1 Authorization 头（包级可见便于单测）。 */
    String sign(String verb, String contentMd5, String contentType, String date, String key) {
        // 该账号必须三级域名访问；OSS 校验时 CanonicalizedResource 仍为 /{bucket}/{key}
        String stringToSign = verb + "\n"
                + contentMd5 + "\n"
                + contentType + "\n"
                + date + "\n"
                + "/" + props.bucket() + "/" + key;
        return "OSS " + props.accessKeyId() + ":" + hmacSha1Base64(props.accessKeySecret(), stringToSign);
    }

    static String hmacSha1Base64(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String base64Md5(byte[] data) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static void validateKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()
                || key.contains("..") || key.contains("//") || key.endsWith("/")) {
            throw new IllegalArgumentException("invalid oss object key");
        }
    }

    private void ensureConfigured() {
        if (props.bucket() == null || props.bucket().isBlank()
                || props.accessKeyId() == null || props.accessKeyId().isBlank()
                || props.accessKeySecret() == null || props.accessKeySecret().isBlank()) {
            throw new BusinessException(ErrorCode.AUDIO_STORAGE_FAILED, "oss storage not configured");
        }
    }

    private static String normalizeEndpoint(String endpoint) {
        String trimmed = endpoint.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
