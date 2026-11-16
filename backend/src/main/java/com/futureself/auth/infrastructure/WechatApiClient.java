package com.futureself.auth.infrastructure;

import tools.jackson.databind.JsonNode;
import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.config.AppWechatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 微信 code2session 客户端（实施文档 §18）。
 * mock-login=true 时不访问微信：code 确定性映射为 mock openid（开发/集成测试用）。
 * 禁止记录 app_secret、session_key 或完整请求 URL。
 */
@Component
public class WechatApiClient {

    private static final Logger log = LoggerFactory.getLogger(WechatApiClient.class);

    private final AppWechatProperties props;
    private final RestClient restClient;

    public WechatApiClient(AppWechatProperties props) {
        this.props = props;
        this.restClient = RestClient.builder().build();
    }

    /** code2session 结果。session_key 仅后端内部使用，禁止下发前端或写日志。 */
    public record WechatSession(String openid, String sessionKey, String unionid) {
    }

    public WechatSession code2Session(String code) {
        if (props.mockLogin()) {
            return mockSession(code);
        }
        try {
            JsonNode body = restClient.get()
                    .uri(uri -> uri.scheme("https")
                            .host(hostOf(props.apiBaseUrl()))
                            .path("/sns/jscode2session")
                            .queryParam("appid", props.appId())
                            .queryParam("secret", props.appSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || body.hasNonNull("errcode") && body.get("errcode").asInt() != 0) {
                int errcode = body == null ? -1 : body.path("errcode").asInt(-1);
                log.warn("wechat code2session failed, errcode={}", errcode);
                throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
            }
            String openid = body.path("openid").asText(null);
            if (openid == null || openid.isBlank()) {
                throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
            }
            return new WechatSession(openid, body.path("session_key").asText(null), body.path("unionid").asText(null));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("wechat code2session request failed: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.WECHAT_LOGIN_FAILED);
        }
    }

    /** 确定性映射：同一 code 总是同一 openid，保证“再次登录识别同一用户”验收可用。 */
    private WechatSession mockSession(String code) {
        return new WechatSession("mock_" + sha256Hex(code).substring(0, 24), "mock-session-key", null);
    }

    private static String hostOf(String baseUrl) {
        String url = baseUrl.startsWith("http") ? baseUrl : "https://" + baseUrl;
        return java.net.URI.create(url).getHost();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
