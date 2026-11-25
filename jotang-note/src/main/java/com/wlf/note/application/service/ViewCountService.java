package com.wlf.note.application.service;

import com.wlf.note.domain.note.NoteQueryRepository;
import com.wlf.note.domain.note.NoteRepository;
import com.wlf.note.domain.note.ViewCountBuffer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/** 浏览量的记录、展示与定时回写。 */
@Slf4j
@Service
public class ViewCountService {

    private final ViewCountBuffer viewCountBuffer;
    private final NoteQueryRepository noteQueryRepository;
    private final NoteRepository noteRepository;
    private final TransactionTemplate transactionTemplate;

    public ViewCountService(ViewCountBuffer viewCountBuffer,
                            NoteQueryRepository noteQueryRepository,
                            NoteRepository noteRepository,
                            PlatformTransactionManager transactionManager) {
        this.viewCountBuffer = viewCountBuffer;
        this.noteQueryRepository = noteQueryRepository;
        this.noteRepository = noteRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 记一次浏览，返回含本次的总量；Redis 不可用时返回 null。 */
    public Long recordView(Long noteId) {
        try {
            Long total = viewCountBuffer.increment(noteId);
            if (total == null) {
                long base = noteQueryRepository.findViewCount(noteId).orElse(0L);
                viewCountBuffer.initializeIfAbsent(noteId, base);
                total = viewCountBuffer.increment(noteId);
            }
            return total;
        } catch (RuntimeException e) {
            log.warn("记录浏览量失败，本次不计数 noteId={}", noteId, e);
            return null;
        }
    }

    /** 当前总量；Redis 不可用或计数未初始化时返回 null。 */
    public Long current(Long noteId) {
        try {
            return viewCountBuffer.totals(List.of(noteId)).get(noteId);
        } catch (RuntimeException e) {
            log.warn("读取浏览量失败，回退快照值 noteId={}", noteId, e);
            return null;
        }
    }

    /** 把 Redis 中待回写的总量刷进 DB，返回本批写库的笔记数。 */
    public int flush(int limit) {

        // 1.取出“这一轮要回写DB”的笔记 ID 批次
        List<Long> dirtyIds;
        try {
            dirtyIds = viewCountBuffer.dirtyNoteIds(limit);
        } catch (RuntimeException e) {
            log.warn("读取待回写浏览量失败，本轮跳过", e);
            return 0;
        }
        if (dirtyIds.isEmpty()) {
            return 0;
        }

        // 2.批量拿最新总量
        Map<Long, Long> totals;
        try {
            totals = viewCountBuffer.totals(dirtyIds);
        } catch (RuntimeException e) {
            log.warn("读取浏览量总量失败，本轮跳过", e);
            return 0;
        }

            // 清僵尸脏标记：有些 noteId 还在待回写集合里，但对应的计数器已经不存在了
        List<Long> missing = dirtyIds.stream().filter(id -> !totals.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            discardQuietly(missing);
        }
        if (totals.isEmpty()) {
            return 0;
        }

        // 3.一个事务批量写DB
        try {
            transactionTemplate.executeWithoutResult(status -> noteRepository.saveViewCounts(totals));
        } catch (RuntimeException e) {
            log.error("回写浏览量失败，保留脏标记待下一轮 totals={}", totals, e);
            return 0;
        }

        // 4.逐个清脏标记（值变了就不清，下轮再写）
        for (Map.Entry<Long, Long> entry : totals.entrySet()) {
            try {
                viewCountBuffer.clearIfUnchanged(entry.getKey(), entry.getValue());
            } catch (RuntimeException e) {
                log.warn("清理浏览量脏标记失败，下一轮会重复回写 noteId={}", entry.getKey(), e);
            }
        }
        return totals.size();
    }

    private void discardQuietly(List<Long> noteIds) {
        try {
            viewCountBuffer.discard(noteIds);
        } catch (RuntimeException e) {
            log.warn("清理失效浏览量脏标记失败 noteIds={}", noteIds, e);
        }
    }
}
