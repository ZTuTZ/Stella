package com.futureself.admin.application;

import com.futureself.common.error.BusinessException;
import com.futureself.config.AppAdminProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * H-11：登录失败限流的 Redis 故障降级行为。
 */
class AdminLoginThrottleTest {

    private AppAdminProperties props() {
        return new AppAdminProperties("admin", "x", 5, 15);
    }

    @Test
    void redisFailureDoesNotBlockLogin() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new IllegalStateException("redis down"));
        when(ops.increment(anyString())).thenThrow(new IllegalStateException("redis down"));
        when(redis.delete(anyString())).thenThrow(new IllegalStateException("redis down"));

        AdminLoginThrottle throttle = new AdminLoginThrottle(redis, props());
        assertThatCode(() -> throttle.assertNotLocked("127.0.0.1", "admin")).doesNotThrowAnyException();
        assertThatCode(() -> throttle.recordFailure("127.0.0.1", "admin")).doesNotThrowAnyException();
        assertThatCode(() -> throttle.clear("127.0.0.1", "admin")).doesNotThrowAnyException();
    }

    @Test
    void lockedAccountIsRejected() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn("5");

        AdminLoginThrottle throttle = new AdminLoginThrottle(redis, props());
        assertThatThrownBy(() -> throttle.assertNotLocked("127.0.0.1", "admin"))
                .isInstanceOf(BusinessException.class);
    }
}
