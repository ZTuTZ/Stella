package com.futureself.auth.application;

import com.futureself.config.AppAuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Access Token 服务（实施文档 §18.3）：随机 opaque token + Redis。
 * Redis 只存 token 哈希 -> userId（TTL 与 token 一致），便于主动失效；
 * 不在 Redis 存任何业务状态（§17）。
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final String KEY_PREFIX = "fs:auth:token:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final AppAuthProperties authProps;

    public TokenService(StringRedisTemplate redis, AppAuthProperties authProps) {
        this.redis = redis;
        this.authProps = authProps;
    }

    public record IssuedToken(String accessToken, long expiresInSeconds) {
    }

    public IssuedToken issue(long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Duration ttl = Duration.ofSeconds(authProps.tokenTtlSeconds());
        redis.opsForValue().set(KEY_PREFIX + sha256Hex(token), String.valueOf(userId), ttl);
        return new IssuedToken(token, authProps.tokenTtlSeconds());
    }

    public Optional<Long> resolveUserId(String token) {
        try {
            String userId = redis.opsForValue().get(KEY_PREFIX + sha256Hex(token));
            if (userId == null || userId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(userId));
        } catch (RuntimeException e) {
            // Redis 不可用时鉴权 fail-closed（§17：核心数据仍在数据库，token 缓存不可降级为放行）
            log.warn("token resolve failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void revoke(String token) {
        redis.delete(KEY_PREFIX + sha256Hex(token));
    }

    private static String sha256Hex(String input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
