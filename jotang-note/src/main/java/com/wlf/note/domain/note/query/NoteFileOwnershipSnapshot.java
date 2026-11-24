package com.wlf.note.domain.note.query;

import com.wlf.note.domain.note.NoteStatus;

/** 附件的归属、状态与 OSS 位置。 */
public record NoteFileOwnershipSnapshot(Long uploaderId,
                                        String status,
                                        String storageKey,
                                        Long size,
                                        String contentType) {

    public NoteStatus noteStatus() {
        return NoteStatus.fromName(status);
    }
}
