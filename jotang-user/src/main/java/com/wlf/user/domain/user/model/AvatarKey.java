package com.wlf.user.domain.user.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 头像对象键，形如 {@code avatars/{userId}/{uuid}.{ext}}。 */
public record AvatarKey(String value) {

    public static final int MAX_LENGTH = 255;
    public static final String RULE_MESSAGE = "头像对象键不合法";

    private static final Pattern PATTERN =
            Pattern.compile("^avatars/(\\d+)/[0-9a-fA-F-]{36}\\.(jpg|jpeg|png|webp)$");

    public AvatarKey {
        if (value == null || value.length() > MAX_LENGTH || !PATTERN.matcher(value).matches()) {
            throw UserViolation.invalid("avatar", RULE_MESSAGE);
        }
    }

    /** 对象键里的 userId 是否就是本人。 */
    public boolean belongsTo(Long userId) {
        if (userId == null) {
            return false;
        }
        Matcher matcher = PATTERN.matcher(value);
        return matcher.matches() && Long.parseLong(matcher.group(1)) == userId;
    }
}
