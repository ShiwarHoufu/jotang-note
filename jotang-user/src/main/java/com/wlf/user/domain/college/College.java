package com.wlf.user.domain.college;

import java.util.Objects;

/** 学院：预置的只读参照数据，用户按 id 引用。 */
public final class College {

    private final Long id;
    private final String name;

    private College(Long id, String name) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
    }

    /** 仅供仓储重建。 */
    public static College reconstitute(Long id, String name) {
        return new College(id, name);
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }
}
