package com.wlf.note.application.result;

import java.util.List;

/** 分页结果。 */
public record PageResult<T>(List<T> items, long total, int page, int size) {
}
