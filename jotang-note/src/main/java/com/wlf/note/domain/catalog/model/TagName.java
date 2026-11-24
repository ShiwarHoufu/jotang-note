package com.wlf.note.domain.catalog.model;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;

import java.util.List;

/** 标签名：trim 后非空，不超过 32 个字符。 */
public record TagName(String value) {

    public static final int MAX_LENGTH = 32;
    public static final String RULE_MESSAGE = "单个标签不超过 32 个字符";

    public TagName {
        value = value == null ? null : value.trim();
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(),
                    List.of(new FieldViolation("tags", RULE_MESSAGE)));
        }
    }
}
