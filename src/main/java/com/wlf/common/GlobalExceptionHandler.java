package com.wlf.common;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：业务异常、参数校验失败、兜底异常统一翻译为 ApiResponse。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
}
