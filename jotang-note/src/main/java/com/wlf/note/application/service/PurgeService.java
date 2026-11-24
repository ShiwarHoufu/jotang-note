package com.wlf.note.application.service;

import com.wlf.note.domain.note.NoteQueryRepository;
import com.wlf.note.domain.note.NoteRepository;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.domain.note.query.PendingPurgeSnapshot;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/** 孤儿对象对账。 */
@Slf4j
@Service
public class PurgeService {

    private final NoteQueryRepository noteQueryRepository;
    private final NoteRepository noteRepository;
    private final StorageService storageService;

    public PurgeService(NoteQueryRepository noteQueryRepository,
                        NoteRepository noteRepository,
                        StorageService storageService) {
        this.noteQueryRepository = noteQueryRepository;
        this.noteRepository = noteRepository;
        this.storageService = storageService;
    }

    /**
     * 跑一轮对账。
     *
     * @param limit 一轮最多处理多少条
     * @return 本轮确实清掉对象的条数
     */
    public int sweep(int limit) {
        List<PendingPurgeSnapshot> pending = noteQueryRepository.findPendingPurge(NoteStatus.DELETED, limit);

        int purged = 0;
        for (PendingPurgeSnapshot row : pending) {
            if (purgeObjectThenMark(row.noteId(), row.storageKey())) {
                purged++;
            }
        }
        return purged;
    }

    private boolean purgeObjectThenMark(Long noteId, String storageKey) {
        try {
            storageService.delete(Bucket.NOTE, storageKey);
        } catch (RuntimeException e) {
            log.error("清理笔记对象失败，is_purged 保持 0 待对账 noteId={} key={}", noteId, storageKey, e);
            return false;
        }

        noteRepository.markFilePurged(noteId);
        return true;
    }
}
