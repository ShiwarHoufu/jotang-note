package com.wlf.note.domain.catalog;

import java.util.List;

/**
 * 课程参照数据的读取端口。
 */
public interface CourseRepository {

    /** id 为 null 时返回 false。 */
    boolean existsById(Long id);

    /** keyword 非空时按课程名模糊匹配；恒按「其他」置末、id 升序。 */
    List<Course> search(String keyword);
}
