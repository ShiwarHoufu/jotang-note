package com.wlf.note.domain.note.model;

/** 笔记标题：trim 后非空，不超过 128 个字符。 */
public record NoteTitle(String value) {

    public static final int MAX_LENGTH = 128;
    public static final String RULE_MESSAGE = "标题不能为空且不超过 128 个字符";

    public NoteTitle {
        value = value == null ? null : value.trim();
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            throw NoteViolation.invalid("title", RULE_MESSAGE);
        }
    }
}
