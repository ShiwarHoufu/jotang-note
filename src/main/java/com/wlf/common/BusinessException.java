package com.wlf.common;

import lombok.Getter;

/**
 * 业务异常，携带 {@link ErrorCode}，由 GlobalExceptionHandler 统一翻译成响应体。
 * 见《概要设计》§5.7。
 *
 * <p>文案沿用 {@link RuntimeException#getMessage()}，默认取错误码的 message，
 * 需要说明具体原因时（如「文件超过 100MB」）可覆写。
 *
 * <p>与 ApiResponse 同理，不提供 {@code BusinessException(ErrorCode, Object data)}——
 * 它会与 {@code (ErrorCode, String)} 擦除后同形，需要携带数据时走三参构造。
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
