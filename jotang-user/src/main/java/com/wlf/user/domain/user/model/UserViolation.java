package com.wlf.user.domain.user.model;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;

import java.util.List;

/** 值对象内部的校验失败出口，统一拼成 40001 + 字段明细。 */
final class UserViolation {

    private UserViolation() {
    }

    static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(),
                List.of(new FieldViolation(field, message)));
    }

    static BusinessException serverError(String message) {
        return new BusinessException(ErrorCode.SERVER_ERROR, message);
    }
}
