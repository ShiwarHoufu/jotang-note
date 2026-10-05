package com.wlf.common;

/**
 * 单个字段的校验失败明细，作为 {@link ErrorCode#PARAM_INVALID} 响应的 data 元素。
 * 见《概要设计》§5.7。
 *
 * <p>命名为 Violation 而非 FieldError，是为了避开 Spring 的
 * {@code org.springframework.validation.FieldError}。
 *
 * @param field   出错的字段名，如 {@code title}
 * @param message 面向用户的原因说明
 */
public record FieldViolation(String field, String message) {
}
