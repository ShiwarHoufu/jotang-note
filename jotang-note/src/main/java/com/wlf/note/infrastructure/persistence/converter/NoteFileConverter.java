package com.wlf.note.infrastructure.persistence.converter;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.note.domain.note.NoteFile;
import com.wlf.note.domain.note.model.ContentType;
import com.wlf.note.domain.note.model.FileSize;
import com.wlf.note.domain.note.model.OriginalFileName;
import com.wlf.note.domain.note.model.StorageKey;
import com.wlf.note.infrastructure.persistence.po.NoteFilePO;

/** NoteFile ↔ note_file 行。 */
public final class NoteFileConverter {

    private NoteFileConverter() {
    }

    public static NoteFile toDomain(NoteFilePO po) {
        if (po == null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "笔记缺少附件");
        }
        return NoteFile.reconstitute(
                new StorageKey(po.getStorageKey()),
                new OriginalFileName(po.getOriginalName()),
                new FileSize(po.getSize()),
                new ContentType(po.getContentType()),
                po.getIsPurged() != null && po.getIsPurged() == 1);
    }

    public static NoteFilePO toPO(NoteFile file, Long noteId) {
        NoteFilePO po = new NoteFilePO();
        po.setNoteId(noteId);
        po.setStorageKey(file.storageKey().value());
        po.setOriginalName(file.originalName().value());
        po.setSize(file.size().value());
        po.setContentType(file.contentType().value());
        po.setIsPurged(file.isPurged() ? 1 : 0);
        return po;
    }
}
