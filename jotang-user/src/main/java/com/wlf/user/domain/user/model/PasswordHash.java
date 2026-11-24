package com.wlf.user.domain.user.model;

/** 已编码的密码凭证，落库的唯一形式。 */
public record PasswordHash(String value) {

    public static final int MAX_LENGTH = 100;

    public PasswordHash {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            throw UserViolation.serverError("密码凭证不合法");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[******]";
    }
}
