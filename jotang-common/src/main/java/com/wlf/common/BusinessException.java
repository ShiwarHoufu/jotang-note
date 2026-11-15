package com.wlf.common;

import lombok.Getter;

/**
 * 业务异常，携带 {@link ErrorCode}，由 GlobalExceptionHandler 统一翻译成响应体。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 附加业务数据，随响应体的 data 返回；无则为 null */
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }
}
