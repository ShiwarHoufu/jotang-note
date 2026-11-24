package com.wlf.note.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.note.domain.note.Note;
import com.wlf.note.domain.note.NoteRepository;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.infrastructure.persistence.NoteFileMapper;
import com.wlf.note.infrastructure.persistence.NoteMapper;
import com.wlf.note.infrastructure.persistence.NoteTagMapper;
import com.wlf.note.infrastructure.persistence.converter.NoteConverter;
import com.wlf.note.infrastructure.persistence.converter.NoteFileConverter;
import com.wlf.note.infrastructure.persistence.po.NoteFilePO;
import com.wlf.note.infrastructure.persistence.po.NotePO;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 写侧仓储的 MyBatis 实现。 */
@Repository
public class NoteRepositoryImpl implements NoteRepository {

    private final NoteMapper noteMapper;
    private final NoteFileMapper noteFileMapper;
    private final NoteTagMapper noteTagMapper;

    public NoteRepositoryImpl(NoteMapper noteMapper,
                              NoteFileMapper noteFileMapper,
                              NoteTagMapper noteTagMapper) {
        this.noteMapper = noteMapper;
        this.noteFileMapper = noteFileMapper;
        this.noteTagMapper = noteTagMapper;
    }

    @Override
    public Optional<Note> findById(Long id) {
        NotePO po = noteMapper.selectById(id);
        return po == null ? Optional.empty() : Optional.of(loadAggregate(po));
    }

    @Override
    public Optional<Note> findByIdForUpdate(Long id) {
        NotePO po = noteMapper.selectByIdForUpdate(id);
        return po == null ? Optional.empty() : Optional.of(loadAggregate(po));
    }

    @Override
    public Optional<NoteStatus> lockStatus(Long id) {
        String status = noteMapper.selectStatusForUpdate(id);
        return status == null ? Optional.empty() : Optional.of(NoteStatus.fromName(status));
    }

    @Override
    public void insert(Note note) {
        NotePO po = NoteConverter.toPO(note);
        noteMapper.insert(po);
        note.assignId(po.getId());

        noteFileMapper.insert(NoteFileConverter.toPO(note.file(), note.id()));
        if (!note.tagIds().isEmpty()) {
            noteTagMapper.insertBatch(note.id(), List.copyOf(note.tagIds()));
        }
    }

    @Override
    public void saveMetadata(Note note) {
        noteMapper.updateMetadata(note.id(),
                note.title().value(),
                note.summary().value(),
                note.teacher().value(),
                note.courseId().value());
    }

    @Override
    public void replaceTags(Long noteId, Collection<Long> tagIds) {
        noteTagMapper.deleteByNoteId(noteId);
        if (tagIds != null && !tagIds.isEmpty()) {
            noteTagMapper.insertBatch(noteId, List.copyOf(tagIds));
        }
    }

    @Override
    public void saveStatus(Note note) {
        if (note.status() == NoteStatus.DELETED) {
            noteMapper.markDeleted(note.id(), note.status().name());
        } else {
            noteMapper.updateStatus(note.id(), note.status().name());
        }
    }

    @Override
    public void markFilePurged(Long noteId) {
        noteFileMapper.update(null, Wrappers.<NoteFilePO>lambdaUpdate()
                .eq(NoteFilePO::getNoteId, noteId)
                .eq(NoteFilePO::getIsPurged, 0)
                .set(NoteFilePO::getIsPurged, 1));
    }

    @Override
    public int incrementViewCount(Long noteId, NoteStatus onlyWhen) {
        return noteMapper.incrementViewCount(noteId, onlyWhen.name());
    }

    @Override
    public int incrementFavoriteCount(Long noteId) {
        return noteMapper.incrementFavoriteCount(noteId);
    }

    @Override
    public int decrementFavoriteCount(Long noteId) {
        return noteMapper.decrementFavoriteCount(noteId);
    }

    @Override
    public long favoriteCount(Long noteId) {
        Long count = noteMapper.selectFavoriteCount(noteId);
        return count == null ? 0L : count;
    }

    private Note loadAggregate(NotePO po) {
        NoteFilePO filePo = noteFileMapper.selectOne(
                Wrappers.<NoteFilePO>lambdaQuery().eq(NoteFilePO::getNoteId, po.getId()));
        List<Long> tagIds = noteTagMapper.selectTagIds(po.getId());
        return NoteConverter.toDomain(po, filePo, tagIds);
    }
}
