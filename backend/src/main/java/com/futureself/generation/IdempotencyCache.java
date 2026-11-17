package com.futureself.generation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Idempotency-Key 的 Redis 缓存（H-06）：仅作加速，DB 唯一键才是事实源。
 * 任何 Redis 异常都静默降级为 cache miss/写失败，绝不影响创建主流程。
 */
@Component
public class IdempotencyCache {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCache.class);
    private static final String PREFIX = "fs:idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public IdempotencyCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<Long> find(long userId, String key) {
        try {
            String value = redis.opsForValue().get(PREFIX + userId + ":" + key);
            return value == null ? Optional.empty() : Optional.of(Long.parseLong(value));
        } catch (RuntimeException e) {
            log.warn("idempotency cache read degraded: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void put(long userId, String key, long taskId) {
        try {
            redis.opsForValue().set(PREFIX + userId + ":" + key, String.valueOf(taskId), TTL);
        } catch (RuntimeException e) {
            log.warn("idempotency cache write degraded: {}", e.getClass().getSimpleName());
        }
    }
}
