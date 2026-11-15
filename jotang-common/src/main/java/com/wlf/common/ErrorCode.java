package com.wlf.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 错误码枚举。
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    OK(0, "ok", HttpStatus.OK),

    PARAM_INVALID(40001, "参数校验失败", HttpStatus.BAD_REQUEST),

    UNAUTHORIZED(40100, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),

    BAD_CREDENTIALS(40101, "用户名或密码错误", HttpStatus.UNAUTHORIZED),

    FORBIDDEN(40300, "无权限", HttpStatus.FORBIDDEN),

    NOTE_UNAVAILABLE(40301, "笔记已下架或已删除", HttpStatus.FORBIDDEN),

    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),

    USERNAME_TAKEN(40901, "该用户名已被占用", HttpStatus.CONFLICT),

    ALREADY_FAVORITED(40902, "已收藏过该笔记", HttpStatus.CONFLICT),

    EMAIL_TAKEN(40903, "该邮箱已被注册", HttpStatus.CONFLICT),

    FILE_INVALID(42200, "文件类型或大小不合法", HttpStatus.UNPROCESSABLE_ENTITY),

    ACCOUNT_LOCKED(42300, "账号已锁定", HttpStatus.LOCKED),

    AI_RATE_LIMITED(42900, "生成过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),

    SERVER_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    AI_UNAVAILABLE(50300, "摘要生成服务暂不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);

    /** 业务码，0 表示成功 */
    private final int code;
    /** 默认提示文案。需要更具体时用 {@link ApiResponse#fail(ErrorCode, String)} 覆写 */
    private final String message;
    /** 该业务码对应的 HTTP 状态码，由 GlobalExceptionHandler 写入响应 */
    private final HttpStatus httpStatus;

    /** 按业务码反查；未知码退回 {@link #SERVER_ERROR}。 */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return SERVER_ERROR;
    }
}
