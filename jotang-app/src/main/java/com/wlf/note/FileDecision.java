package com.wlf.note;

/**
 * 文件准入判定的结果。
 *
 * <p>{@code originalName} 与 {@code contentType}
 * 写进 {@code note_file}，{@code extension} 用于拼对象键与日志排查。
 *
 * <p>previewMode 预览方式由 {@link PreviewMode#of(String)} 在读取侧现推。
 *
 * @param originalName 已清洗过路径的原始文件名
 * @param extension    已归一化为小写的扩展名，不含点
 * @param contentType  服务端判定值，写入 OSS 对象元数据与 {@code note_file.content_type}。
 */
public record FileDecision(String originalName, String extension, String contentType) {
}
