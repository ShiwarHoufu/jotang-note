package com.wlf.note.domain.note.model;

import java.util.regex.Pattern;

/** 笔记对象的 OSS 键，形如 {@code notes/yyyy/MM/{uuid}.{ext}}。 */
public record StorageKey(String value) {

    public static final int MAX_LENGTH = 255;

    private static final Pattern PATTERN =
            Pattern.compile("^notes/\\d{4}/\\d{2}/[0-9a-fA-F-]{36}\\.(md|txt|jpg|jpeg|png|gif|webp)$");

    public StorageKey {
        if (value == null || value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches()) {
            throw NoteViolation.serverError("存储键不合法");
        }
    }
}
