package com.futureself.profile.api;

import java.util.List;
import java.util.Map;

/**
 * Life Profile 响应（GET/PUT /api/v1/me/life-profile）。
 */
public record LifeProfileResponse(
        Long userId,
        String currentSituation,
        List<String> focusAreas,
        List<ImportantPerson> importantPeople,
        Map<String, String> preferences,
        int profileVersion) {
}
