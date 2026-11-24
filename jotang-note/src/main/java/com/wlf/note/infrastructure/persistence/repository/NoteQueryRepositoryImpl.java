package com.wlf.note.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wlf.note.domain.note.NoteQueryRepository;
import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.domain.note.query.MyNoteSnapshot;
import com.wlf.note.domain.note.query.NoteDetailSnapshot;
import com.wlf.note.domain.note.query.NoteFileOwnershipSnapshot;
import com.wlf.note.domain.note.query.NoteListCriteria;
import com.wlf.note.domain.note.query.NoteListSnapshot;
import com.wlf.note.domain.note.query.NoteSearchSnapshot;
import com.wlf.note.domain.note.query.NoteTagRefSnapshot;
import com.wlf.note.domain.note.query.PendingPurgeSnapshot;
import com.wlf.note.domain.shared.PageSlice;
import com.wlf.note.infrastructure.persistence.NoteMapper;
import com.wlf.note.infrastructure.persistence.po.NotePO;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 读侧仓储的 MyBatis 实现。 */
@Repository
public class NoteQueryRepositoryImpl implements NoteQueryRepository {

    private final NoteMapper noteMapper;

    public NoteQueryRepositoryImpl(NoteMapper noteMapper) {
        this.noteMapper = noteMapper;
    }

    @Override
    public Optional<NoteDetailSnapshot> findDetail(Long noteId) {
        return Optional.ofNullable(noteMapper.selectDetail(noteId));
    }

    @Override
    public List<NoteTagRefSnapshot> findTags(Long noteId) {
        return noteMapper.selectTagRefs(noteId);
    }

    @Override
    public List<NoteTagRefSnapshot> findTagsByNoteIds(Collection<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return List.of();
        }
        return noteMapper.selectTagRefsByNoteIds(noteIds);
    }

    @Override
    public PageSlice<NoteListSnapshot> findPage(NoteListCriteria criteria) {
        IPage<NoteListSnapshot> result = noteMapper.selectListPage(
                new Page<>(criteria.page(), criteria.size()),
                NoteStatus.ONLINE.name(),
                criteria.sort().name(),
                criteria.courseId(),
                criteria.tagId(),
                criteria.keyword());
        return new PageSlice<>(result.getRecords(), result.getTotal());
    }

    @Override
    public PageSlice<MyNoteSnapshot> findMine(Long uploaderId, int page, int size) {
        IPage<MyNoteSnapshot> result = noteMapper.selectMyNotePage(
                new Page<>(page, size), uploaderId, NoteStatus.DELETED.name());
        return new PageSlice<>(result.getRecords(), result.getTotal());
    }

    @Override
    public Optional<NoteFileOwnershipSnapshot> findFileOwnership(Long noteId) {
        return Optional.ofNullable(noteMapper.selectFileOwnership(noteId));
    }

    @Override
    public List<PendingPurgeSnapshot> findPendingPurge(NoteStatus status, int limit) {
        return noteMapper.selectPendingPurge(status.name(), limit);
    }

    @Override
    public List<NoteSearchSnapshot> findOnlineByExactTitle(String title, int limit) {
        return noteMapper.selectList(Wrappers.<NotePO>lambdaQuery()
                        .eq(NotePO::getStatus, NoteStatus.ONLINE.name())
                        .eq(NotePO::getTitle, title.strip())
                        .orderByDesc(NotePO::getUpdatedAt)
                        .orderByDesc(NotePO::getId)
                        .last("LIMIT " + limit))
                .stream()
                .map(po -> new NoteSearchSnapshot(po.getId(), po.getTitle(), po.getTeacher(), po.getSummary()))
                .toList();
    }
}
