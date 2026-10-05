package com.wlf.common;

import org.springframework.http.HttpStatus;

/**
 * 错误码枚举，取值见《概要设计》§5.7 统一错误码表。
 * HTTP状态码：给传输层看（浏览器、Nginx、Axios拦截器、监控告警）
 * code：细分给应用层看。
 */
public enum ErrorCode {

    /** 成功。唯一非错误取值，供统一响应体复用 */
    OK(0, "ok", HttpStatus.OK),

    /** 参数校验失败。data 携带字段级错误明细 [{field, message}] */
    PARAM_INVALID(40001, "参数校验失败", HttpStatus.BAD_REQUEST),

    /** 未登录 / token 无效 / token 过期。*/
    UNAUTHORIZED(40100, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),

    /** 登录时用户名或密码错误。前端动作：登录表单内提示，不跳转——与 40100 的「跳登录」区别开 */
    BAD_CREDENTIALS(40101, "用户名或密码错误", HttpStatus.UNAUTHORIZED),

    /** 无权限：非本人操作、非管理员调用管理接口 */
    FORBIDDEN(40300, "无权限", HttpStatus.FORBIDDEN),

    /** 笔记非 ONLINE（已下架 / 已删除），禁止预览、下载、收藏。具体是下架还是删除由调用方从详情接口的 status 得知 */
    NOTE_UNAVAILABLE(40301, "笔记已下架或已删除", HttpStatus.FORBIDDEN),

    /** 资源不存在 */
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),

    /** 注册时用户名已被占用。前端动作：表单字段标红 */
    USERNAME_TAKEN(40901, "该用户名已被占用", HttpStatus.CONFLICT),

    /** 重复收藏。前端动作：按钮置为「已收藏」态 */
    ALREADY_FAVORITED(40902, "已收藏过该笔记", HttpStatus.CONFLICT),

    /** 注册时邮箱已被占用。与 USERNAME_TAKEN 同为 409，前端据 code 决定标红哪个输入框 */
    EMAIL_TAKEN(40903, "该邮箱已被注册", HttpStatus.CONFLICT),

    /** 文件类型或大小不合法。具体原因（扩展名 / 魔数 / 大小）由 message 说明 */
    FILE_INVALID(42200, "文件类型或大小不合法", HttpStatus.UNPROCESSABLE_ENTITY),

    /** 账号已锁定。data 携带剩余锁定秒数，供前端倒计时 */
    ACCOUNT_LOCKED(42300, "账号已锁定", HttpStatus.LOCKED),

    /** 服务器内部错误：未预期的异常统一兜底为该码 */
    SERVER_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    /** 默认提示文案。需要更具体时用 {@link ApiResponse#fail(ErrorCode, String)} 覆写 */
    public String getMessage() {
        return message;
    }

    /** 该业务码对应的 HTTP 状态码，由 GlobalExceptionHandler 写入响应 */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
