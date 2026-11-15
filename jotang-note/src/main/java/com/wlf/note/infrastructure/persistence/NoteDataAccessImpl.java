package com.wlf.note.infrastructure.persistence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.note.application.catalog.dto.TagResponse;
import com.wlf.note.application.note.dto.MyNoteRow;
import com.wlf.note.application.note.dto.NoteDetailRow;
import com.wlf.note.application.note.dto.NoteFileOwnershipRow;
import com.wlf.note.application.note.dto.NoteListRow;
import com.wlf.note.application.note.dto.NoteOwnershipRow;
import com.wlf.note.application.note.dto.NoteTagRef;
import com.wlf.note.application.note.dto.PendingPurgeRow;
import com.wlf.note.application.port.out.NoteDataAccess;
import com.wlf.note.domain.note.Note;
import com.wlf.note.domain.note.NoteFile;
import com.wlf.note.domain.note.NoteStatus;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 笔记数据端口的 MyBatis 实现。
 */
@Repository
public class NoteDataAccessImpl implements NoteDataAccess {

    private final NoteMapper noteMapper;
    private final NoteFileMapper noteFileMapper;
    private final NoteTagMapper noteTagMapper;

    public NoteDataAccessImpl(NoteMapper noteMapper,
                              NoteFileMapper noteFileMapper,
                              NoteTagMapper noteTagMapper) {
        this.noteMapper = noteMapper;
        this.noteFileMapper = noteFileMapper;
        this.noteTagMapper = noteTagMapper;
    }

    @Override
    public NoteDetailRow selectDetail(Long id) {
        return noteMapper.selectDetail(id);
    }

    @Override
    public List<TagResponse> selectTagRefs(Long noteId) {
        return noteMapper.selectTagRefs(noteId);
    }

    @Override
    public IPage<NoteListRow> selectListPage(IPage<NoteListRow> page, String status, String sort,
                                             Long courseId, Long tagId, String keyword) {
        return noteMapper.selectListPage(page, status, sort, courseId, tagId, keyword);
    }

    @Override
    public IPage<MyNoteRow> selectMyNotePage(IPage<MyNoteRow> page, Long uploaderId, String excludedStatus) {
        return noteMapper.selectMyNotePage(page, uploaderId, excludedStatus);
    }

    @Override
    public List<NoteTagRef> selectTagRefsByNoteIds(Collection<Long> noteIds) {
        return noteMapper.selectTagRefsByNoteIds(noteIds);
    }

    @Override
    public int incrementViewCount(Long id, String status) {
        return noteMapper.incrementViewCount(id, status);
    }

    @Override
    public int incrementFavoriteCount(Long id) {
        return noteMapper.incrementFavoriteCount(id);
    }

    @Override
    public int decrementFavoriteCount(Long id) {
        return noteMapper.decrementFavoriteCount(id);
    }

    @Override
    public Long selectFavoriteCount(Long id) {
        return noteMapper.selectFavoriteCount(id);
    }

    @Override
    public String selectStatusForUpdate(Long id) {
        return noteMapper.selectStatusForUpdate(id);
    }

    @Override
    public NoteOwnershipRow selectOwnershipForUpdate(Long id) {
        return noteMapper.selectOwnershipForUpdate(id);
    }

    @Override
    public NoteFileOwnershipRow selectFileOwnership(Long id) {
        return noteMapper.selectFileOwnership(id);
    }

    @Override
    public List<PendingPurgeRow> selectPendingPurge(String status, int limit) {
        return noteMapper.selectPendingPurge(status, limit);
    }

    @Override
    public int updateStatus(Long id, String status) {
        return noteMapper.updateStatus(id, status);
    }

    @Override
    public int markDeleted(Long id, String status) {
        return noteMapper.markDeleted(id, status);
    }

    @Override
    public int updateMetadata(Long id, String title, String summary, String teacher, Long courseId) {
        return noteMapper.updateMetadata(id, title, summary, teacher, courseId);
    }

    @Override
    public void insert(Note note) {
        noteMapper.insert(note);
    }

    @Override
    public void insertFile(NoteFile noteFile) {
        noteFileMapper.insert(noteFile);
    }

    @Override
    public void markFilePurged(Long noteId) {
        noteFileMapper.update(null, Wrappers.<NoteFile>lambdaUpdate()
                .eq(NoteFile::getNoteId, noteId)
                .eq(NoteFile::getIsPurged, 0)
                .set(NoteFile::getIsPurged, 1));
    }

    @Override
    public void insertTags(Long noteId, List<Long> tagIds) {
        noteTagMapper.insertBatch(noteId, tagIds);
    }

    @Override
    public void deleteTags(Long noteId) {
        noteTagMapper.deleteByNoteId(noteId);
    }

    @Override
    public List<Note> findOnlineByExactTitle(String title, int limit) {
        return noteMapper.selectList(Wrappers.<Note>lambdaQuery()
                .eq(Note::getStatus, NoteStatus.ONLINE.name())
                .eq(Note::getTitle, title.strip())
                .orderByDesc(Note::getUpdatedAt)
                .orderByDesc(Note::getId)
                .last("LIMIT " + limit));
    }
}
