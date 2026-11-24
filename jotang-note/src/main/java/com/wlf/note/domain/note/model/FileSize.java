package com.wlf.note.domain.note.model;

/** 笔记文件大小：正数，不超过 100MB。 */
public record FileSize(long value) {

    public static final long MAX_BYTES = 100L * 1024 * 1024;

    public FileSize {
        if (value <= 0) {
            throw NoteViolation.invalid("file", "文件内容为空");
        }
        if (value > MAX_BYTES) {
            throw NoteViolation.invalid("file", "文件超过 " + (MAX_BYTES >> 20) + "MB");
        }
    }
}
