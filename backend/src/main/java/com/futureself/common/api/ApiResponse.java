package com.futureself.common.api;

import com.futureself.common.error.ErrorCode;
import com.futureself.common.logging.RequestIdFilter;
import org.slf4j.MDC;

/**
 * 统一响应体（实施文档 §12）：
 * <pre>{ "success": true, "code": "OK", "message": "", "data": {}, "requestId": "..." }</pre>
 */
public record ApiResponse<T>(boolean success, String code, String message, T data, String requestId) {

    public static final String OK_CODE = "OK";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, OK_CODE, "", data, currentRequestId());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, OK_CODE, "", null, currentRequestId());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, code.name(), message == null ? code.name() : message, null, currentRequestId());
    }

    private static String currentRequestId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId == null ? "" : requestId;
    }
}
