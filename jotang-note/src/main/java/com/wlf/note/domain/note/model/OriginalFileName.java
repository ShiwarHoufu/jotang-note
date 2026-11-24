package com.wlf.note.domain.note.model;

/** 原始文件名：去掉客户端可能送来的路径，trim 后非空，不超过 255 个字符。 */
public record OriginalFileName(String value) {

    public static final int MAX_LENGTH = 255;

    public OriginalFileName {
        if (value == null) {
            throw NoteViolation.invalid("file", "缺少文件名");
        }
        String name = value.trim();
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        if (name.isBlank()) {
            throw NoteViolation.invalid("file", "文件名不合法");
        }
        if (name.length() > MAX_LENGTH) {
            throw NoteViolation.invalid("file", "文件名超过 " + MAX_LENGTH + " 个字符");
        }
        value = name;
    }
}
