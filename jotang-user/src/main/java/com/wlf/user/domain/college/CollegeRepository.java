package com.wlf.user.domain.college;

import java.util.List;

/**
 * 学院参照数据的读取端口。
 */
public interface CollegeRepository {

    /** 全部学院，按 id 升序。 */
    List<College> findAllOrderedById();

    /** id 为 null 时返回 false。 */
    boolean existsById(Long id);
}
