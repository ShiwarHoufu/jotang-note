package com.wlf.note;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * note_tag 关联表的数据访问。
 *
 * <p>复合主键（{@code note_id, tag_id}）没有单列自增 id，{@link com.baomidou.mybatisplus.core.mapper.BaseMapper}
 * 那套「按主键增删改查」在这里没有意义，故不继承它，SQL 自写。
 *
 * <p>用注解而不是 XML：这条语句不参与检索侧的多表 JOIN，也没有动态分支要维护，
 * 为一个 {@code INSERT} 引入一份 XML 不划算。note 模块的 XML 留给列表 / 搜索。
 */
@Mapper
public interface NoteTagMapper {

    /**
     * 一次插入一篇笔记的全部标签关联。
     *
     * <p>批量而非逐条：上传时标签数是已知的个位数，拼成一条 INSERT 少几次往返。
     * 关联表上没有任何业务列（没有「打标时间」这类字段），因此不需要 {@code INSERT IGNORE}
     * 之类的去重保护——{@code TagService#resolveIds} 已保证入参里没有重复的 tagId，
     * 同一篇笔记也不会被插入两次。
     *
     * @param tagIds 非空；调用方需保证已去重
     */
    @Insert("""
            <script>
            INSERT INTO note_tag (note_id, tag_id) VALUES
            <foreach collection="tagIds" item="tagId" separator=",">
                (#{noteId}, #{tagId})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("noteId") Long noteId, @Param("tagIds") List<Long> tagIds);
}
