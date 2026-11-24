package com.wlf.user.domain.user.model;

import java.util.Locale;
import java.util.regex.Pattern;

/** 邮箱：trim 后统一小写，最长 128 字符。 */
public record Email(String value) {

    public static final int MAX_LENGTH = 128;
    public static final String RULE_MESSAGE = "邮箱格式不正确";

    private static final Pattern PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    public Email {
        value = value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        if (value == null || value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches()) {
            throw UserViolation.invalid("email", RULE_MESSAGE);
        }
    }
}
