package com.futureself.goal.api;

import java.util.Map;

/**
 * 目标响应（CRUD 接口）。details 为追问问题/答案等补充信息（§10.3 details_json）。
 */
public record GoalResponse(
        Long id,
        String category,
        String title,
        String description,
        String targetTimeText,
        Map<String, Object> details,
        String status,
        String createdAt) {
}
