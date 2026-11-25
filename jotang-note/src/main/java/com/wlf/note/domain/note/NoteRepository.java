package com.wlf.note.domain.note;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 写侧：以聚合为单位加载与保存。 */
public interface NoteRepository {

    /** 加载完整聚合：note + note_file + note_tag。 */
    Optional<Note> findById(Long id);

    /** 同上，并锁住 note 行。 */
    Optional<Note> findByIdForUpdate(Long id);

    /** 只锁 note 行并返回状态，供收藏路径使用。 */
    Optional<NoteStatus> lockStatus(Long id);

    /** 插入聚合：note、note_file、note_tag。 */
    void insert(Note note);

    /** 保存元数据，不碰标签、状态与计数。 */
    void saveMetadata(Note note);

    /** 全量替换标签。 */
    void replaceTags(Long noteId, Collection<Long> tagIds);

    /** 保存状态；DELETED 时同时写 deleted_at。 */
    void saveStatus(Note note);

    void markFilePurged(Long noteId);

    /** 按绝对总量回写浏览量，只增不减。 */
    void saveViewCounts(Map<Long, Long> totals);

    int incrementFavoriteCount(Long noteId);

    int decrementFavoriteCount(Long noteId);

    long favoriteCount(Long noteId);
}
