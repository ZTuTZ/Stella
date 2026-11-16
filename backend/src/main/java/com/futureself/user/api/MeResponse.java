package com.futureself.user.api;

/**
 * 当前用户响应（GET/PATCH /api/v1/me、onboarding/complete）。
 */
public record MeResponse(long id, String displayName, boolean onboardingCompleted) {
}
