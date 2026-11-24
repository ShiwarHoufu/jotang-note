package com.wlf.note.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wlf.note.application.catalog.dto.TagResponse;
import com.wlf.note.application.note.dto.MyNoteRow;
import com.wlf.note.application.note.dto.NoteDetailRow;
import com.wlf.note.application.note.dto.NoteFileOwnershipRow;
import com.wlf.note.application.note.dto.NoteListRow;
import com.wlf.note.application.note.dto.NoteOwnershipRow;
import com.wlf.note.application.note.dto.NoteTagRef;
import com.wlf.note.application.note.dto.PendingPurgeRow;
import com.wlf.note.domain.note.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * note 表的数据访问：单表 CRUD 由 {@link BaseMapper} 提供，跨表检索走 {@code NoteMapper.xml}。
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /**
     * 详情主干（note、note_file、course；上传者另走 UserProfileReader）。
     * 不筛 status：已下架的笔记也要查得出来，是否展示由 Service 决定。
     */
    NoteDetailRow selectDetail(@Param("id") Long id);

    /** 某篇笔记的标签，按 tag.id 升序。to-many，不并进详情主干。 */
    List<TagResponse> selectTagRefs(@Param("noteId") Long noteId);

    /**
     * 列表 / 搜索的一页，只返回 ONLINE。
     *
     * <p>首个参数必须是 {@code IPage}，分页插件靠它识别并生成 count 语句。
     * {@code sort} 传排序白名单的 {@code name()}，避免 SQL 里拼接 {@code ${}}。
     *
     * @param tagId   按标签筛选；等值 JOIN 至多匹配一行，不会让分页行数翻倍
     * @param keyword 已由 {@code NoteService#escapeLikeKeyword} 转义过的 LIKE 模式
     */
    IPage<NoteListRow> selectListPage(IPage<NoteListRow> page,
                                      @Param("status") String status,
                                      @Param("sort") String sort,
                                      @Param("courseId") Long courseId,
                                      @Param("tagId") Long tagId,
                                      @Param("keyword") String keyword);

    /** 一批笔记的标签，避免 N+1。noteIds 必须非空，空页应在调用方短路。 */
    List<NoteTagRef> selectTagRefsByNoteIds(@Param("noteIds") Collection<Long> noteIds);

    /**
     * 「我的上传」的一页。
     * DELETED 在 SQL 里排除：放到 Service 过滤会让 total 把已删除的也算进去。
     */
    IPage<MyNoteRow> selectMyNotePage(IPage<MyNoteRow> page,
                                      @Param("uploaderId") Long uploaderId,
                                      @Param("excludedStatus") String excludedStatus);

    /** 浏览量原子自增，仅当笔记处于给定状态；状态由调用方传入。 */
    @Update("UPDATE note SET view_count = view_count + 1 WHERE id = #{id} AND status = #{status}")
    int incrementViewCount(@Param("id") Long id, @Param("status") String status);

    /**
     * 收藏量原子自增。{@code favorite_count} 是 note 的列，故放在这里；
     * 调用方 {@code FavoriteService} 已用 {@link #selectStatusForUpdate} 持有该行 X 锁，不再带状态守卫。
     */
    @Update("UPDATE note SET favorite_count = favorite_count + 1 WHERE id = #{id}")
    int incrementFavoriteCount(@Param("id") Long id);

    /** 收藏量原子自减。{@code > 0} 是计数漂移时的兜底，避免出现负数。 */
    @Update("UPDATE note SET favorite_count = favorite_count - 1 WHERE id = #{id} AND favorite_count > 0")
    int decrementFavoriteCount(@Param("id") Long id);

    /** 只读收藏量；笔记不存在时为 null。 */
    @Select("SELECT favorite_count FROM note WHERE id = #{id}")
    Long selectFavoriteCount(@Param("id") Long id);

    /**
     * 锁住笔记行并读出状态。收藏、取消收藏、管理员下架 / 恢复共用。
     * 收藏路径靠它统一锁序 note → favorite，避免外键共享锁升级为死锁。
     */
    @Select("SELECT status FROM note WHERE id = #{id} FOR UPDATE")
    String selectStatusForUpdate(@Param("id") Long id);

    /**
     * 锁住笔记行并读归属与状态，供删除 / 编辑做校验。
     * 调用方持有 X 锁后，后续写语句不必再带归属或状态守卫。
     */
    @Select("SELECT uploader_id, status FROM note WHERE id = #{id} FOR UPDATE")
    NoteOwnershipRow selectOwnershipForUpdate(@Param("id") Long id);

    /** 同 {@link #selectOwnershipForUpdate}，但不加锁：ai 编辑场景只读，拿到对象键后还要读 OSS、调模型。 */
    NoteFileOwnershipRow selectFileOwnership(@Param("id") Long id);

    /** 孤儿对象对账候选：已删除且未清理的笔记；排除空 storage_key，免得每轮重复选中。 */
    List<PendingPurgeRow> selectPendingPurge(@Param("status") String status, @Param("limit") int limit);

    /** 管理员下架 / 恢复：只改 status，不碰 updated_at 与 deleted_at。 */
    @Update("UPDATE note SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 软删除：置状态并写 deleted_at。 */
    @Update("UPDATE note SET status = #{status}, deleted_at = NOW() WHERE id = #{id}")
    int markDeleted(@Param("id") Long id, @Param("status") String status);

    /** 编辑元数据：改四个字段并显式写 updated_at；不碰 status、计数列与 created_at / deleted_at。 */
    @Update("""
            UPDATE note
            SET title      = #{title},
                summary    = #{summary},
                teacher    = #{teacher},
                course_id  = #{courseId},
                updated_at = NOW()
            WHERE id = #{id}
            """)
    int updateMetadata(@Param("id") Long id,
                       @Param("title") String title,
                       @Param("summary") String summary,
                       @Param("teacher") String teacher,
                       @Param("courseId") Long courseId);
}
