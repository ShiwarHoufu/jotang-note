package com.wlf.note.domain.note.model;

/** 教师名：可选，trim 后空白归一为 null，不超过 64 个字符。 */
public record TeacherName(String value) {

    public static final int MAX_LENGTH = 64;
    public static final String RULE_MESSAGE = "教师不超过 64 个字符";

    public TeacherName {
        value = value == null ? null : value.trim();
        if (value != null && value.isEmpty()) {
            value = null;
        }
        if (value != null && value.length() > MAX_LENGTH) {
            throw NoteViolation.invalid("teacher", RULE_MESSAGE);
        }
    }
}
