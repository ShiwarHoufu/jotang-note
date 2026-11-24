package com.wlf.user.domain.passwordreset.model;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;

import java.util.regex.Pattern;

/** 令牌哈希：SHA-256 的小写 hex，固定 64 字符。 */
public record TokenHash(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public TokenHash {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "令牌哈希不合法");
        }
    }
}
