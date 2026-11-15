package com.wlf.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传场景的入参。
 * 一个文件（图片或 md / txt）+ 可选的上下文。
 */
@Getter
@Setter
public class AiSummaryRequest {

    /** 待摘要的文件。交给模型前会过 {@code AiSummaryFilePolicy} 的白名单与大小上限 */
    @NotNull(message = "请选择文件")
    private MultipartFile file;

    @Size(max = 128, message = "标题不超过 128 个字符")
    private String title;

    @Size(max = 64, message = "教师不超过 64 个字符")
    private String teacher;
}
