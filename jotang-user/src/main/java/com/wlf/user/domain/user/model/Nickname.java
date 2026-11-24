package com.wlf.user.domain.user.model;

/** 昵称：trim 后非空，不超过 32 字符，不要求唯一。 */
public record Nickname(String value) {

    public static final int MAX_LENGTH = 32;
    public static final String RULE_MESSAGE = "昵称不能为空且不超过 32 个字符";

    public Nickname {
        value = value == null ? null : value.trim();
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            throw UserViolation.invalid("nickname", RULE_MESSAGE);
        }
    }

    public static Nickname fromUsername(Username username) {
        return new Nickname(username.value());
    }
}
