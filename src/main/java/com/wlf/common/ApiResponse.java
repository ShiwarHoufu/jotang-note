package com.wlf.common;

import lombok.Getter;

/**
 * 统一响应体 {code, message, data}。
 * 见《概要设计》§5。
 *
 * <p>不可变：字段 final、只有静态工厂、无 setter，构造完成后不可能被改写。
 *
 * <p>成功与失败共用同一结构（而非成功返 data、失败返 error 的两套），
 * 前端拦截器只需判断一处 code。
 *
 * <p>{@code data} 为 null 时仍保留该字段，保证响应体形状恒定：
 * 前端不必额外做存在性判断，Apifox 上的示例也稳定。
 */
@Getter
public class ApiResponse<T> {

    /** 业务码，0 表示成功，其余见 {@link ErrorCode} */
    private final int code;
    /** 提示文案，直接面向用户 */
    private final String message;
    /** 业务数据，无数据时为 null */
    private final T data;

    private ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功、无数据（删除、退出等） */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), null);
    }

    /** 成功、带数据 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), data);
    }

    /** 失败，用错误码的默认文案 */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 失败，覆写文案以说明具体原因 */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }

    /** 失败，同时携带数据（40001 的字段明细 / 42300 的剩余锁定秒数） */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(errorCode.getCode(), message, data);
    }
}
