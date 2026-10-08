package com.wlf.note;

/**
 * 文件准入判定的结果，由 {@link NoteFilePolicy#inspect} 产出。
 *
 * <p>四个字段正好是落库与交付各自需要的东西：{@code originalName} 与 {@code contentType}
 * 写进 {@code note_file}，{@code previewMode} 决定前端怎么渲染，{@code extension} 用于拼对象键
 * （拼法由 {@link NoteFilePolicy#objectKey} 收口）与日志排查。
 *
 * @param originalName 已清洗过路径的原始文件名，下载时用它还原文件名
 * @param extension    已归一化为小写的扩展名，不含点
 * @param contentType  服务端判定值，写入 OSS 对象元数据与 {@code note_file.content_type}。
 *                     读取时不再覆写（§6.2），故这里定下的就是最终值
 * @param previewMode  预览方式，见 {@link PreviewMode}
 */
public record FileDecision(String originalName, String extension, String contentType, PreviewMode previewMode) {
}
