package com.futureself.common.error;

/**
 * 业务错误码与 HTTP 状态映射（实施文档 §22）。
 * 前端不直接展示底层异常栈或供应商原始错误。
 */
public enum ErrorCode {

    AUTH_REQUIRED(401),
    AUTH_INVALID_TOKEN(401),
    WECHAT_LOGIN_FAILED(401),
    USER_DISABLED(403),
    PROFILE_NOT_FOUND(404),
    GOAL_NOT_FOUND(404),
    GOAL_CONTENT_BLOCKED(422),
    STORY_NOT_FOUND(404),
    TASK_NOT_FOUND(404),
    TASK_ALREADY_RUNNING(409),
    TASK_RETRY_LIMIT_REACHED(409),
    TEXT_AI_UNAVAILABLE(502),
    TEXT_AI_INVALID_RESPONSE(502),
    AUDIO_PROVIDER_NOT_CONFIGURED(503),
    AUDIO_SUBMIT_FAILED(502),
    AUDIO_PROCESSING_FAILED(502),
    AUDIO_STORAGE_FAILED(500),
    MODERATION_BLOCKED(422),
    RATE_LIMITED(429),
    VALIDATION_FAILED(400),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
