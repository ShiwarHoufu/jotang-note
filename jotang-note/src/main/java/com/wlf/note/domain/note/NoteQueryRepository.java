package com.wlf.note.domain.note;

import com.wlf.note.domain.note.query.AdminNoteSnapshot;
import com.wlf.note.domain.note.query.MyNoteSnapshot;
import com.wlf.note.domain.note.query.NoteDetailSnapshot;
import com.wlf.note.domain.note.query.NoteFileOwnershipSnapshot;
import com.wlf.note.domain.note.query.NoteListCriteria;
import com.wlf.note.domain.note.query.NoteListSnapshot;
import com.wlf.note.domain.note.query.NoteSearchSnapshot;
import com.wlf.note.domain.note.query.NoteTagRefSnapshot;
import com.wlf.note.domain.note.query.PendingPurgeSnapshot;
import com.wlf.note.domain.shared.PageSlice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 读侧：只返回投影。 */
public interface NoteQueryRepository {

    Optional<NoteDetailSnapshot> findDetail(Long noteId);

    List<NoteTagRefSnapshot> findTags(Long noteId);

    List<NoteTagRefSnapshot> findTagsByNoteIds(Collection<Long> noteIds);

    PageSlice<NoteListSnapshot> findPage(NoteListCriteria criteria);

    PageSlice<MyNoteSnapshot> findMine(Long uploaderId, int page, int size);

    PageSlice<AdminNoteSnapshot> findOfflinePage(int page, int size, String keyword);

    Optional<NoteFileOwnershipSnapshot> findFileOwnership(Long noteId);

    List<PendingPurgeSnapshot> findPendingPurge(NoteStatus status, int limit);

    List<NoteSearchSnapshot> findOnlineByExactTitle(String title, int limit);
}
