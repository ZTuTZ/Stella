package com.futureself.common.ratelimit;

import com.futureself.common.time.BusinessTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Redis 固定窗口计数限流（实施文档 §24）。
 * key：fs:rate:{userId}:{operation}:{yyyyMMddHH | yyyyMMdd}（§17）；
 * 窗口为业务时区（Asia/Shanghai）的整点/整日，TTL = 窗口 + 冗余（时窗 +65min、日窗 +25h）。
 * 说明：V1.0.1 使用固定窗口而非滑动窗口（H-02），保证“每小时 N 次”语义正确。
 * Redis 不可用时降级放行并 warn（§17：不得因 Redis 重启阻塞核心流程）。
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;
    private final Clock clock;

    @Autowired
    public RateLimitService(StringRedisTemplate redis) {
        this(redis, Clock.system(BusinessTime.ZONE));
    }

    /** 可注入 Clock 便于跨窗口测试（包级可见）。 */
    RateLimitService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    /** 小时窗限流（创建 Goal 30/时、AI 追问 20/时）。 */
    public boolean allowHourly(long userId, String operation, int limit) {
        String window = LocalDateTime.now(clock).format(HOUR_FORMAT);
        return allow("fs:rate:" + userId + ":" + operation + ":" + window, limit, Duration.ofMinutes(65));
    }

    /** 日窗限流（生成故事 10/日）。 */
    public boolean allowDaily(long userId, String operation, int limit) {
        String window = LocalDate.now(clock).format(DAY_FORMAT);
        return allow("fs:rate:" + userId + ":" + operation + ":" + window, limit, Duration.ofHours(25));
    }

    /** 日窗 + 资源维度限流（重试音频 5/日/故事）。 */
    public boolean allowDailyScoped(long userId, String operation, long scopeId, int limit) {
        String window = LocalDate.now(clock).format(DAY_FORMAT);
        return allow("fs:rate:" + userId + ":" + operation + ":" + scopeId + ":" + window, limit, Duration.ofHours(25));
    }

    private boolean allow(String key, int limit, Duration ttl) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, ttl);
            }
            return count != null && count <= limit;
        } catch (RuntimeException e) {
            log.warn("rate limit degraded (redis unavailable): {}", e.getClass().getSimpleName());
            return true;
        }
    }
}
