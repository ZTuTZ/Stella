package com.futureself.admin.application;

import com.futureself.common.error.BusinessException;
import com.futureself.common.error.ErrorCode;
import com.futureself.config.AppAdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * 管理端登录失败限流（H-11）：维度 = IP + usernameHash，固定窗口。
 * 阈值/窗口可配置；Redis 故障降级放行（BCrypt 仍照常校验）并 warn。
 * 不记录原始用户名/密码。
 */
@Component
public class AdminLoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginThrottle.class);
    private static final String PREFIX = "fs:admin:loginfail:";

    private final StringRedisTemplate redis;
    private final AppAdminProperties props;

    public AdminLoginThrottle(StringRedisTemplate redis, AppAdminProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** 超过阈值直接拒绝（429），避免继续做 BCrypt 校验。 */
    public void assertNotLocked(String ip, String username) {
        try {
            String value = redis.opsForValue().get(key(ip, username));
            int count = value == null ? 0 : Integer.parseInt(value);
            if (count >= props.loginMaxFailures()) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "登录失败次数过多，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("admin login throttle degraded: {}", e.getClass().getSimpleName());
        }
    }

    public void recordFailure(String ip, String username) {
        try {
            String key = key(ip, username);
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofMinutes(props.loginWindowMinutes()));
            }
        } catch (RuntimeException e) {
            log.warn("admin login throttle record degraded: {}", e.getClass().getSimpleName());
        }
    }

    public void clear(String ip, String username) {
        try {
            redis.delete(key(ip, username));
        } catch (RuntimeException e) {
            log.warn("admin login throttle clear degraded: {}", e.getClass().getSimpleName());
        }
    }

    private String key(String ip, String username) {
        return PREFIX + ip + ":" + hash(username);
    }

    private static String hash(String username) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest((username == null ? "" : username).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", out[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unavailable";
        }
    }
}
