package com.wlf.note.application.port.out;

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
import com.wlf.note.domain.note.NoteFile;

import java.util.Collection;
import java.util.List;

/**
 * 笔记数据的出站端口：把 MyBatis 查询收在基础设施层，应用层只依赖这些语义方法。
 */
public interface NoteDataAccess {

    NoteDetailRow selectDetail(Long id);

    List<TagResponse> selectTagRefs(Long noteId);

    IPage<NoteListRow> selectListPage(IPage<NoteListRow> page, String status, String sort,
                                      Long courseId, Long tagId, String keyword);

    IPage<MyNoteRow> selectMyNotePage(IPage<MyNoteRow> page, Long uploaderId, String excludedStatus);

    List<NoteTagRef> selectTagRefsByNoteIds(Collection<Long> noteIds);

    int incrementViewCount(Long id, String status);

    int incrementFavoriteCount(Long id);

    int decrementFavoriteCount(Long id);

    Long selectFavoriteCount(Long id);

    String selectStatusForUpdate(Long id);

    NoteOwnershipRow selectOwnershipForUpdate(Long id);

    NoteFileOwnershipRow selectFileOwnership(Long id);

    List<PendingPurgeRow> selectPendingPurge(String status, int limit);

    int updateStatus(Long id, String status);

    int markDeleted(Long id, String status);

    int updateMetadata(Long id, String title, String summary, String teacher, Long courseId);

    void insert(Note note);

    void insertFile(NoteFile noteFile);

    void markFilePurged(Long noteId);

    void insertTags(Long noteId, List<Long> tagIds);

    void deleteTags(Long noteId);

    List<Note> findOnlineByExactTitle(String title, int limit);
}
