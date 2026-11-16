package com.futureself.goal.api;

import jakarta.validation.constraints.Size;

/**
 * 更新目标（PATCH /api/v1/goals/{id}）：字段可选，只更新提供的字段（PATCH 语义）。
 */
public record UpdateGoalRequest(
        String goalCategory,
        @Size(min = 2, max = 80, message = "goalTitle must be 2~80 chars") String goalTitle,
        @Size(min = 10, max = 1200, message = "goalDescription must be 10~1200 chars") String goalDescription,
        @Size(max = 120, message = "targetTimeText must be <= 120 chars") String targetTimeText) {
}
