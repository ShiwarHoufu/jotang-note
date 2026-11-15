package com.wlf.note.infrastructure.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * note_tag 关联表的数据访问。
 */
@Mapper
public interface NoteTagMapper {

    /**
     * 一次插入一篇笔记的全部标签关联。
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
     * <p>编辑的标签是全量替换，所以这里是先删净。
     * @return 受影响行数。
     */
    @Delete("DELETE FROM note_tag WHERE note_id = #{noteId}")
    int deleteByNoteId(@Param("noteId") Long noteId);
}
