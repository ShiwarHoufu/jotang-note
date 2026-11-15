package com.wlf.note.domain.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 标签的持久化端口。
 */
public interface TagRepository {

    /** 使用量最高的前 limit 个。 */
    List<Tag> selectMostUsed(int limit);

    List<Tag> findByNameIn(Collection<String> names);

    Optional<Tag> findByName(String name);

    /** 插入后自增主键回填到入参对象。 */
    void insert(Tag tag);
}
