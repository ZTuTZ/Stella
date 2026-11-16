package com.futureself.ai.audio;

/**
 * 提交结果：供应商任务 ID（异步）或 null（同步供应商直接在 query 中给出结果 URL）。
 */
public record AudioSubmitResult(String providerTaskId) {
}
