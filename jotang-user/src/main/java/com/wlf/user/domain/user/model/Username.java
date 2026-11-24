package com.wlf.user.domain.user.model;

import java.util.Locale;
import java.util.regex.Pattern;

/** 登录名：3~32 位字母、数字、下划线，保留原始大小写。 */
public record Username(String value) {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 32;
    public static final String RULE_MESSAGE = "用户名长度需为 3~32 个字符，且只能包含字母、数字和下划线";

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");

    public Username {
        value = value == null ? null : value.trim();
        if (value == null || value.length() < MIN_LENGTH || value.length() > MAX_LENGTH
                || !PATTERN.matcher(value).matches()) {
            throw UserViolation.invalid("username", RULE_MESSAGE);
        }
    }

    /** 数据库排序规则大小写不敏感，内存侧比较与限频统一用这个口径。 */
    public String normalized() {
        return value.toLowerCase(Locale.ROOT);
    }
}
