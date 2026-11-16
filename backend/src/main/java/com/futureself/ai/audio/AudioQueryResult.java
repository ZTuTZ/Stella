package com.futureself.ai.audio;

/**
 * 查询结果（实施文档 §15.2/§15.4）。
 * status: PROCESSING（仍在生成）/ READY（url 可下载）/ FAILED。
 * url 仅内部转存使用，禁止直接下发给前端（§10.5 provider_url）。
 */
public record AudioQueryResult(
        JobStatus status,
        String url,
        String format,
        Long durationMs,
        String errorCode,
        String errorMessage) {

    public enum JobStatus {
        PROCESSING,
        READY,
        FAILED
    }
}
