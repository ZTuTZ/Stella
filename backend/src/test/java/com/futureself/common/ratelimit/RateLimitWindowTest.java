package com.futureself.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * H-02：固定小时窗口（同一小时内 N 次通过、N+1 次拒绝；跨小时重置；Redis 故障降级放行）。
 */
class RateLimitWindowTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private StringRedisTemplate fakeRedis(Map<String, Long> counters) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(inv -> counters.merge(inv.getArgument(0), 1L, Long::sum));
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
        return redis;
    }

    @Test
    void hourlyWindowUsesWholeHourKey() {
        Map<String, Long> counters = new HashMap<>();
        Clock clock = Clock.fixed(Instant.parse("2026-11-17T02:10:00Z"), SHANGHAI); // 10:10
        RateLimitService svc = new RateLimitService(fakeRedis(counters), clock);

        assertThat(svc.allowHourly(7, "goal-create", 2)).isTrue();
        assertThat(svc.allowHourly(7, "goal-create", 2)).isTrue();
        assertThat(svc.allowHourly(7, "goal-create", 2)).isFalse();
        assertThat(counters.keySet()).anyMatch(k -> k.endsWith(":2026111710"));
    }

    @Test
    void nextHourWindowResets() {
        Map<String, Long> counters = new HashMap<>();
        RateLimitService first = new RateLimitService(fakeRedis(counters),
                Clock.fixed(Instant.parse("2026-11-17T02:59:00Z"), SHANGHAI)); // 10:59
        assertThat(first.allowHourly(8, "goal-create", 1)).isTrue();
        assertThat(first.allowHourly(8, "goal-create", 1)).isFalse();

        RateLimitService next = new RateLimitService(fakeRedis(counters),
                Clock.fixed(Instant.parse("2026-11-17T03:00:01Z"), SHANGHAI)); // 11:00
        assertThat(next.allowHourly(8, "goal-create", 1)).isTrue();
    }

    @Test
    void dailyWindowStillWorks() {
        Map<String, Long> counters = new HashMap<>();
        RateLimitService svc = new RateLimitService(fakeRedis(counters),
                Clock.fixed(Instant.parse("2026-11-17T02:10:00Z"), SHANGHAI));
        assertThat(svc.allowDaily(9, "story-generate", 1)).isTrue();
        assertThat(svc.allowDaily(9, "story-generate", 1)).isFalse();
    }

    @Test
    void redisFailureDegradesToAllow() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        RateLimitService svc = new RateLimitService(redis, Clock.system(SHANGHAI));
        assertThat(svc.allowHourly(10, "goal-create", 1)).isTrue();
        assertThat(svc.allowDaily(10, "story-generate", 1)).isTrue();
    }
}
