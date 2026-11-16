package com.futureself.common.error;

/**
 * 业务异常：携带 {@link ErrorCode} 与面向用户的安全文案。
 * message 允许覆盖默认文案，但禁止包含底层异常栈/供应商原始错误。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
