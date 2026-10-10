package com.wlf.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传场景的入参：一张图片 + 可选的上下文。见《概要设计》§5.8。
 *
 * <p>用 {@code @ModelAttribute} 绑定而不是 {@code @RequestBody}：multipart 请求里
 * 文件部分不是 JSON，只能按表单字段逐个绑定——与 {@code NoteUploadRequest} 同理。
 *
 * <p><b>{@code file} 打 {@code @NotNull} 而不靠 {@code @RequestParam(required=true)}</b>：
 * 后者缺参时抛的是 {@code MissingServletRequestPartException}，
 * {@code GlobalExceptionHandler} 里没有对应分支，会掉进兜底变成 50000——
 * 「用户忘了选文件」被报成「服务器内部错误」。走 Bean Validation 才能得到一个
 * 带字段明细的 40001。
 *
 * <p>{@code title} / {@code teacher} <b>是可选的上下文，不是校验对象</b>：它们只被拼进 prompt，
 * 帮模型判断这张图讲的是什么（一张板书照片本身没有标题）。前端此刻的表单可能还没填完，
 * 所以缺了也照常生成（§6.8）。上限照抄建表语句的列宽（{@code note.title} 128、
 * {@code note.teacher} 64），理由与上传一致：让超长输入得到字段级的 40001。
 */
@Getter
@Setter
public class AiSummaryRequest {

    /** 待摘要的图片。交给模型前会过 {@code AiSummaryImagePolicy} 的白名单与大小上限 */
    @NotNull(message = "请选择图片")
    private MultipartFile file;

    @Size(max = 128, message = "标题不超过 128 个字符")
    private String title;

    @Size(max = 64, message = "教师不超过 64 个字符")
    private String teacher;
}
