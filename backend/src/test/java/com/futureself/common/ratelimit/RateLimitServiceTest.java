package com.futureself.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8：Redis 限流（§24）——窗口计数、日窗+资源维度隔离。
 * Redis 不可用降级放行的路径由实现保证（catch → true）。
 */
@SpringBootTest
@ActiveProfiles("test")
class RateLimitServiceTest {

    @Autowired
    private RateLimitService rateLimitService;

    private long uniqueUserId() {
        return Math.abs(UUID.randomUUID().getLeastSignificantBits());
    }

    @Test
    void hourlyWindowCountsAndBlocks() {
        long userId = uniqueUserId();
        assertThat(rateLimitService.allowHourly(userId, "test-hourly", 2)).isTrue();
        assertThat(rateLimitService.allowHourly(userId, "test-hourly", 2)).isTrue();
        assertThat(rateLimitService.allowHourly(userId, "test-hourly", 2)).isFalse();
    }

    @Test
    void dailyScopedIsPerResource() {
        long userId = uniqueUserId();
        assertThat(rateLimitService.allowDailyScoped(userId, "test-daily-scoped", 101, 1)).isTrue();
        assertThat(rateLimitService.allowDailyScoped(userId, "test-daily-scoped", 101, 1)).isFalse();
        // 另一资源不受影响
        assertThat(rateLimitService.allowDailyScoped(userId, "test-daily-scoped", 202, 1)).isTrue();
    }
}
