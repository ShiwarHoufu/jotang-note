package com.wlf.note.domain.note;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 浏览量的 Redis 聚合：总量在 Redis，定时回写 DB。 */
public interface ViewCountBuffer {

    /** 记一次浏览并返回累计总量；计数未初始化时返回 null。 */
    Long increment(Long noteId);

    /** 以 DB 值为基准初始化；已有值时不动。 */
    void initializeIfAbsent(Long noteId, long base);

    /** 待回写的 noteId，至多 limit 个。 */
    List<Long> dirtyNoteIds(int limit);

    /** 批量读总量；缺失的 id 不出现在结果中。 */
    Map<Long, Long> totals(Collection<Long> noteIds);

    /** 仅当总量仍等于回写值时才清脏标记。 */
    boolean clearIfUnchanged(Long noteId, long writtenTotal);

    /** 清掉计数已丢失的脏标记。 */
    void discard(Collection<Long> noteIds);
}
