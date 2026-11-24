package com.wlf.note.domain.shared;

import java.util.List;

/** 一页结果：条目 + 总数。 */
public record PageSlice<T>(List<T> items, long total) {
}
