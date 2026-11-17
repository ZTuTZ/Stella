package com.futureself.common.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 业务时区统一入口（实施文档 v1.0.1 §3.2）：所有“今天/本小时”都以 Asia/Shanghai 为准，
 * 不依赖服务器默认时区。
 */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BusinessTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
