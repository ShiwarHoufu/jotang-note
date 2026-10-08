package com.wlf.note;

import org.apache.ibatis.annotations.Delete;
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

    /**
     * 清空一篇笔记的全部标签关联，供编辑重写标签用。
     *
     * <p><b>编辑的标签是「全量替换」，所以这里是先删净再插回，而不是算出差异去增删。</b>
     * 关联表上只有主键两列、没有任何业务列（没有「打标时间」这类字段），
     * 而一篇笔记的标签数上限是 10——差异计算的复杂度换不来任何东西，
     * 还得处理「先删了 A 再删 B 但 B 删不掉」这种半途状态。整段在一个事务里，
     * 删干净再插回，中间没有能被别人观察到的空窗。
     *
     * <p><b>删的是关联，不是标签本身。</b>{@code tag} 表只增不删（见 {@code TagService} 类注释）：
     * 一篇笔记不再用「期末复习」了，不代表别的笔记不用，也不代表这个词该从候选里消失。
     *
     * <p>{@code note_tag} 上挂着 {@code fk_nt_tag}，但那是指向 {@code tag} 的外键，
     * 删本方（子表）的行不受它约束——这里不会因为「标签还在被引用」而失败。
     *
     * @return 受影响行数。0 表示这篇笔记原本就没有标签，属正常情形（清空一个空集合）
     */
    @Delete("DELETE FROM note_tag WHERE note_id = #{noteId}")
    int deleteByNoteId(@Param("noteId") Long noteId);
}
