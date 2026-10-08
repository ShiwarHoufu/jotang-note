package com.wlf.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 上传入参：multipart 表单，文件 + 元数据。见《概要设计》§5.4、§6.1。
 *
 * <p>用 {@code @ModelAttribute} 绑定而不是 {@code @RequestBody}：multipart 请求里
 * 文件部分不是 JSON，只能按表单字段逐个绑定。
 *
 * <p><b>标签按名称提交</b>，不是 tagId 列表：用户可以自由打标，前端虽有候选标签，
 * 但输入框允许直接敲新词。名字到 id 的解析（含按名创建、同名复用）由
 * {@code TagService#resolveIds} 负责，本类不做校验——标签的个数与长度上限同属那条规则，
 * 放在一起才不会两处不一致。
 *
 * <p>这里的 {@code @Size} 上限全部照抄建表语句的列宽（{@code note.title} 128、
 * {@code note.summary} 512、{@code note.teacher} 64）。先在校验层挡住，
 * 是为了让超长输入得到「哪个字段、为什么」的 40001，而不是撞库之后变成 50000。
 */
@Getter
@Setter
public class NoteUploadRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题不超过 128 个字符")
    private String title;

    @Size(max = 512, message = "简介不超过 512 个字符")
    private String summary;

    @Size(max = 64, message = "教师不超过 64 个字符")
    private String teacher;

    @NotNull(message = "请选择课程")
    private Long courseId;

    /** 可空。空白项会被丢弃，重复项会合并，上限见 TagService */
    private List<String> tags;

    /** 单文件，落库后一条笔记对应 {@code note_file} 的一行 */
    @NotNull(message = "请选择文件")
    private MultipartFile file;
}
