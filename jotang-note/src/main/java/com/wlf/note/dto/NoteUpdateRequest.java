package com.wlf.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 编辑入参：JSON 表单，只含元数据。
 *
 * <p>用 {@code @RequestBody} 绑定而不是上传那样的 {@code @ModelAttribute}：这里没有文件部分，
 * 整个请求体就是一份 JSON。这个区别不是风格——它是「编辑不碰附件」这条规则在接口层的体现。
 *
 * <p>本接口是全量替换（PUT），不是局部更新。
 * 这里没有 file 字段，而且不能加。附件只在首次上传时确定，编辑时既不能新增也不能删除
 */
@Getter
@Setter
public class NoteUpdateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题不超过 128 个字符")
    private String title;

    @Size(max = 512, message = "简介不超过 512 个字符")
    private String summary;

    @Size(max = 64, message = "教师不超过 64 个字符")
    private String teacher;

    @NotNull(message = "请选择课程")
    private Long courseId;

    private List<String> tags;
}
