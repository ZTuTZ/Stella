package com.futureself.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新当前用户（PATCH /api/v1/me）。displayName 规则同 onboarding（§5.2：1~30 字符）。
 */
public record UpdateMeRequest(
        @NotBlank(message = "displayName is required")
        @Size(min = 1, max = 30, message = "displayName must be 1~30 chars") String displayName) {
}
