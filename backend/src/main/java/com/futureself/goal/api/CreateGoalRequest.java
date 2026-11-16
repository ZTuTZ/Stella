package com.futureself.goal.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建目标（POST /api/v1/goals，实施文档 §5.2 步骤 C）。
 */
public record CreateGoalRequest(
        @NotBlank(message = "goalCategory is required") String goalCategory,
        @NotBlank(message = "goalTitle is required")
        @Size(min = 2, max = 80, message = "goalTitle must be 2~80 chars") String goalTitle,
        @NotBlank(message = "goalDescription is required")
        @Size(min = 10, max = 1200, message = "goalDescription must be 10~1200 chars") String goalDescription,
        @Size(max = 120, message = "targetTimeText must be <= 120 chars") String targetTimeText) {
}
