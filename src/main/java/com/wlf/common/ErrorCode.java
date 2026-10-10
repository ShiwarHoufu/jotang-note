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

    /**
     * 笔记已下架 / 已删除，禁止预览、下载、收藏、编辑。具体是下架还是删除由调用方从详情接口的 status 得知。
     *
     * <p>「下架」对四个动作并非一律禁止，四处的口径分别是：<b>预览 / 下载 / 收藏</b>要求 ONLINE；
     * <b>编辑</b>只挡 DELETED，OFFLINE 可以改（改完仍是下架状态，不会重新可见）。
     * 这条差别见《概要设计》§4.1、§5.4。
     */
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

    /**
     * AI 摘要调用过于频繁。见《概要设计》§6.8。
     *
     * <p>与 {@link #ACCOUNT_LOCKED} 同属「稍后再来」那一类，但防的东西完全不同：
     * 那个防的是撞库，这个防的是<b>烧钱</b>——每次摘要调用都真金白银地计费，
     * 连点按钮或写脚本刷就一直在花钱。
     */
    AI_RATE_LIMITED(42900, "生成过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),

    /** 服务器内部错误：未预期的异常统一兜底为该码 */
    SERVER_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    /**
     * AI 摘要服务暂不可用：上游超时、连不上、或返回错误。见《概要设计》§6.8。
     *
     * <p><b>与 {@link #SERVER_ERROR} 刻意分开</b>：这个是「上游不可用，稍后重试可能有救」，
     * 那个是「我们的代码出了没预料到的错」。混成一个码，前端就只能给用户一句笼统的
     * 「服务器错误」，也无从判断该不该让用户重试。
     */
    AI_UNAVAILABLE(50300, "摘要生成服务暂不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);

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
