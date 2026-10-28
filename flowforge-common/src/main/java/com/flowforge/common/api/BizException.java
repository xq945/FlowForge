package com.flowforge.common.api;

/**
 * 业务异常。
 *
 * <p>只用于「预期内、需要返回给用户具体错误码」的情况；
 * 真正的程序错误（空指针、SQL 异常等）不应包装成它，否则会被误判为业务失败。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
