package com.futureself.generation;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * H-06：Idempotency 的 Redis 缓存故障必须静默降级（DB 仍是事实源）。
 */
class IdempotencyCacheTest {

    @Test
    void readFailureDegradesToEmpty() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new IllegalStateException("redis down"));

        IdempotencyCache cache = new IdempotencyCache(redis);
        assertThat(cache.find(1L, "key")).isEmpty();
    }

    @Test
    void writeFailureIsSwallowed() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(ops).set(anyString(), anyString(), any(java.time.Duration.class));

        IdempotencyCache cache = new IdempotencyCache(redis);
        assertThatCode(() -> cache.put(1L, "key", 42L)).doesNotThrowAnyException();
    }
}
