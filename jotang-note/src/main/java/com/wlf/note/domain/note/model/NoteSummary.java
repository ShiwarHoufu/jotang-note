package com.wlf.note.domain.note.model;

/** 笔记简介：可选，trim 后空白归一为 null，不超过 512 个字符。 */
public record NoteSummary(String value) {

    public static final int MAX_LENGTH = 512;
    public static final String RULE_MESSAGE = "简介不超过 512 个字符";

    public NoteSummary {
        value = value == null ? null : value.trim();
        if (value != null && value.isEmpty()) {
            value = null;
        }
        if (value != null && value.length() > MAX_LENGTH) {
            throw NoteViolation.invalid("summary", RULE_MESSAGE);
        }
    }
}
