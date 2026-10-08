package com.wlf.note;

/**
 * 文件准入判定的结果，由 {@link NoteFilePolicy#inspect} 产出。
 *
 * <p>三个字段恰好覆盖「落库」与「拼对象键」两件事：{@code originalName} 与 {@code contentType}
 * 写进 {@code note_file}，{@code extension} 用于拼对象键（拼法由
 * {@link NoteFilePolicy#objectKey} 收口）与日志排查。
 *
 * <p><b>这里刻意没有 previewMode</b>：预览方式不是上传期定下的事实，而是 {@code contentType}
 * 的纯函数，由 {@link PreviewMode#of(String)} 在读取侧现推。放在这里会变成同一规则的第二份副本，
 * 且上传链路上根本无人消费它。理由详见 {@link PreviewMode#of(String)}。
 *
 * @param originalName 已清洗过路径的原始文件名，下载时用它还原文件名
 * @param extension    已归一化为小写的扩展名，不含点
 * @param contentType  服务端判定值，写入 OSS 对象元数据与 {@code note_file.content_type}。
 *                     读取时不再覆写（§6.2），故这里定下的就是最终值
 */
public record FileDecision(String originalName, String extension, String contentType) {
}
