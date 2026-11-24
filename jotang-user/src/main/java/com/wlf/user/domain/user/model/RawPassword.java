package com.wlf.user.domain.user.model;

/** 明文密码：只在应用层短暂存在，不 trim，不进数据库。 */
public record RawPassword(String value) {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    public static final String RULE_MESSAGE = "密码长度需为 8~64 个字符";

    public RawPassword {
        if (value == null || value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw UserViolation.invalid("password", RULE_MESSAGE);
        }
    }

    @Override
    public String toString() {
        return "RawPassword[******]";
    }
}
