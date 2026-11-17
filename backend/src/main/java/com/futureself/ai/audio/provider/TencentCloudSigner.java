package com.futureself.ai.audio.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 腾讯云 API 3.0 签名（TC3-HMAC-SHA256）。
 * 参考：https://cloud.tencent.com/document/product/1073/37990
 *
 * <p>只依赖 JDK，不引入 SDK；SecretKey 只在签名计算内使用，禁止写入日志。
 * SignedHeaders 固定为 content-type;host;x-tc-action（长文本接口不要求 Region）。
 */
public final class TencentCloudSigner {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String SIGNED_HEADERS = "content-type;host;x-tc-action";

    private TencentCloudSigner() {
    }

    /** 生成调用腾讯云所需的请求头（含 Authorization）。payload 为最终发送的 JSON 文本。 */
    public static Map<String, String> headers(String action, String version, String host, String service,
                                              String secretId, String secretKey,
                                              String payload, long timestampSeconds) {
        String date = DATE_FORMAT.format(Instant.ofEpochSecond(timestampSeconds));
        String canonicalHeaders = "content-type:" + CONTENT_TYPE + "\n"
                + "host:" + host + "\n"
                + "x-tc-action:" + action.toLowerCase() + "\n";
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n"
                + SIGNED_HEADERS + "\n" + sha256Hex(payload);
        String credentialScope = date + "/" + service + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestampSeconds + "\n"
                + credentialScope + "\n" + sha256Hex(canonicalRequest);

        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, service);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = HexFormat.of().formatHex(hmacSha256(secretSigning, stringToSign));

        String authorization = "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + SIGNED_HEADERS + ", Signature=" + signature;

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", authorization);
        headers.put("Content-Type", CONTENT_TYPE);
        headers.put("X-TC-Action", action);
        headers.put("X-TC-Version", version);
        headers.put("X-TC-Timestamp", String.valueOf(timestampSeconds));
        // Host 由 HTTP 客户端按 URI 自动发送，不显式设置（JDK HttpClient 禁止设置 Host）
        return headers;
    }

    static String sha256Hex(String input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
