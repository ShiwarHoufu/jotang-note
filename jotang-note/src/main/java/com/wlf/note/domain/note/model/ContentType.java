package com.wlf.note.domain.note.model;

import java.util.Set;

/** 服务端判定的文件类型，只接受 NoteFilePolicy 支持的类型。 */
public record ContentType(String value) {

    private static final Set<String> ALLOWED = Set.of(
            "text/markdown", "text/plain",
            "image/jpeg", "image/png", "image/gif", "image/webp");

    public ContentType {
        if (value == null || !ALLOWED.contains(value)) {
            throw NoteViolation.serverError("文件类型不合法");
        }
    }
}
