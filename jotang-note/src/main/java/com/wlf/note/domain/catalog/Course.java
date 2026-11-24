package com.wlf.note.domain.catalog;

/**
 * 课程参照数据。
 *
 * <p>{@code isOther} 标记内置「其他」课程，用户不能自建。
 */
public final class Course {

    private final Long id;
    private final String name;
    private final boolean other;

    private Course(Long id, String name, boolean other) {
        this.id = id;
        this.name = name;
        this.other = other;
    }

    public static Course reconstitute(Long id, String name, boolean other) {
        return new Course(id, name, other);
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean isOther() {
        return other;
    }
}
