package com.wlf.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * tag 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供。候选标签那条读语句直接以 {@code @Select}
 * 写在这里，不为它开 XML：只有几行、也没有动态分支。
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 最常用的标签。
     * 按有多少篇笔记在用倒序取前 {@code limit} 个。
     */
    @Select("""
            SELECT t.id, t.name
            FROM tag t
                     LEFT JOIN note_tag nt ON nt.tag_id = t.id
            GROUP BY t.id, t.name
            ORDER BY COUNT(nt.note_id) DESC, t.id ASC
            LIMIT #{limit}
            """)
    List<Tag> selectMostUsed(@Param("limit") int limit);
}
