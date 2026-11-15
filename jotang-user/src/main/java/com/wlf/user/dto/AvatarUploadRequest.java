package com.wlf.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传头像入参：单个 multipart 文件。见《概要设计》§5.2、§6.7。
 *
 * <p><b>为什么要包一层 DTO，而不是直接 {@code @RequestParam MultipartFile}</b>：缺文件时
 * {@code @RequestParam} 抛的是 {@code MissingServletRequestPartException}，它没有对应的
 * 处理器，会被 {@code GlobalExceptionHandler} 的兜底吃成 50000——「忘了选文件」明明是个
 * 客户端错误。走 {@code @Valid @ModelAttribute} + {@code @NotNull}，缺文件就是 40001 带字段明细，
 * 与 {@code NoteUploadRequest#file} 同一写法。
 *
 * <p>字段名 {@code file} 就是表单里的 part 名（前端 {@code FormData.append('file', blob)}）。
 */
@Getter
@Setter
public class AvatarUploadRequest {

    /** 类型与大小在 {@code AvatarFilePolicy} 里判 */
    @NotNull(message = "请选择头像文件")
    private MultipartFile file;
}
