package com.wlf.note;

import java.io.InputStream;

/**
 * {@code /raw} 要交付的原文：字节数与内容流。见《概要设计》§5.4、§6.2。
 *
 * <p>{@code size} 取自 {@code note_file.size}，用作响应的 {@code Content-Length}。
 * {@code content} 的关闭责任在写出方（即 Spring 的消息转换器），
 * 遵循 {@link com.wlf.storage.StorageService#open} 的约定。
 */
public record NoteRawContent(long size, InputStream content) {
}
