package com.wlf.note.domain.note.model;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.common.FieldViolation;

import java.util.List;

/** 值对象内部的校验失败出口。 */
final class NoteViolation {

    private NoteViolation() {
    }

    static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.PARAM_INVALID, ErrorCode.PARAM_INVALID.getMessage(),
                List.of(new FieldViolation(field, message)));
    }

    static BusinessException serverError(String message) {
        return new BusinessException(ErrorCode.SERVER_ERROR, message);
    }
}
