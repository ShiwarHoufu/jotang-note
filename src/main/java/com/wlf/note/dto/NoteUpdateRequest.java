package com.wlf.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 编辑入参：JSON 表单，只含元数据。见《概要设计》§5.4、§4.1。
 *
 * <p>用 {@code @RequestBody} 绑定而不是上传那样的 {@code @ModelAttribute}：这里没有文件部分，
 * 整个请求体就是一份 JSON。这个区别不是风格——它是「编辑不碰附件」这条规则在接口层的体现。
 *
 * <p><b>本接口是全量替换（PUT），不是局部更新。</b>没传的字段等于「清空」，不等于「不动」：
 * {@code summary} / {@code teacher} 传 null 就是把简介与教师抹掉，{@code tags} 传
 * {@code null} 或空数组就是把标签清空。前端编辑表单本来就是回填全部字段再整体提交，
 * 与 {@code NoteUploadRequest} 是同一套语义。若哪天有人想把它改成 PATCH 的「只改传了的字段」，
 * 要意识到这是个契约变更：同一个 {@code null} 在两种语义下意思相反。
 *
 * <p><b>这里没有 file 字段，而且不能加。</b>附件只在首次上传时确定，编辑时既不能新增也不能删除
 * （§4.1）。加一个文件字段进来，就意味着要处理「新对象传了、事务回滚了」与「旧对象删了、
 * 事务回滚了」这两个窗口——也就是把上传与删除那两套 OSS 生命周期机制同时塞进本接口。
 *
 * <p>校验上限全部照抄建表语句的列宽（{@code note.title} 128、{@code note.summary} 512、
 * {@code note.teacher} 64），理由同上传：让超长输入得到「哪个字段、为什么」的 40001，
 * 而不是撞库之后变成 50000。
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

    /**
     * 可空。空白项会被丢弃、重复项会合并，上限见 {@code TagService}。
     *
     * <p><b>本列表是「编辑之后应有的全部标签」</b>，而不是「这次要加的标签」：传 null 或空数组
     * 会清空该笔记的全部标签。名称到 id 的解析（含按名创建、同名复用）与长度 / 个数上限
     * 仍由 {@code TagService#resolveIds} 一处承担，与上传共用同一条规则。
     */
    private List<String> tags;
}
