package com.wlf.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 全局异常处理：把各类异常统一翻译为 {@link ApiResponse}，并写入对应的 HTTP 状态码。
 * 见《概要设计》§5.7。
 *
 * <p>HTTP 状态码一律取自 {@link ErrorCode#getHttpStatus()}，不在本类里另写一份映射，
 * 避免错误码表改动后两处不一致。
 *
 * <p><b>不覆盖认证 / 鉴权失败</b>：Spring Security 的异常在过滤器链中抛出，
 * 早于 DispatcherServlet，不会到达本处理器。40100 与 40300 由 SecurityConfig 的
 * AuthenticationEntryPoint / AccessDeniedHandler 产出（见 §6.4）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：错误码、文案与 data 均由抛出方决定 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return respond(errorCode, ApiResponse.fail(errorCode, ex.getMessage(), ex.getData()));
    }

    /** {@code @Valid} 校验 {@code @RequestBody} 失败，返回字段级明细 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<FieldViolation>>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldViolation(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.PARAM_INVALID,
                ApiResponse.fail(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(), violations));
    }

    /** {@code @Validated} 校验方法参数 / 路径变量失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<FieldViolation>>> handleParamValidation(ConstraintViolationException ex) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(leafOf(violation), violation.getMessage()))
                .toList();
        return respond(ErrorCode.PARAM_INVALID,
                ApiResponse.fail(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(), violations));
    }

    /** 请求体不是合法 JSON，或字段类型无法转换 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return respond(ErrorCode.PARAM_INVALID, ApiResponse.<Void>fail(ErrorCode.PARAM_INVALID, "请求体格式不正确"));
    }

    /** multipart 超出限制，对应 §6.1 的单文件 100MB / 请求 110MB */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return respond(ErrorCode.FILE_INVALID, ApiResponse.<Void>fail(ErrorCode.FILE_INVALID, "文件超过大小上限"));
    }

    /**
     * 请求路径无对应接口。
     * 若不单独处理，会被下面的兜底吃成 50000——前端写错地址时会误报服务器故障。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(ErrorCode.NOT_FOUND, ApiResponse.<Void>fail(ErrorCode.NOT_FOUND));
    }

    /** 兜底：完整堆栈进日志，响应只给通用文案，不向外泄露内部细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("未处理异常", ex);
        return respond(ErrorCode.SERVER_ERROR, ApiResponse.<Void>fail(ErrorCode.SERVER_ERROR));
    }

    private static <T> ResponseEntity<ApiResponse<T>> respond(ErrorCode errorCode, ApiResponse<T> body) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    /** 取属性路径的最后一段：{@code uploadNotes.arg0.title} → {@code title} */
    private static String leafOf(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }
}
