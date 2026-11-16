package com.futureself.profile.api;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Life Profile 更新请求（PUT /api/v1/me/life-profile，实施文档 §5.2/§12.2）。
 * 校验失败 → 400 VALIDATION_FAILED。
 */
public record LifeProfileUpdateRequest(
        @Size(max = 1000, message = "currentSituation must be <= 1000 chars") String currentSituation,
        @Size(max = 5, message = "focusAreas must contain at most 5 items") List<String> focusAreas,
        @Size(max = 8, message = "importantPeople must contain at most 8 items") List<ImportantPerson> importantPeople,
        @Size(max = 20, message = "preferences must contain at most 20 entries") Map<String, String> preferences) {
}
