package com.wlf.ai.infrastructure.client.note;

import com.wlf.ai.application.port.out.NoteAttachment;
import com.wlf.ai.application.port.out.NoteAttachmentPort;
import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import org.springframework.stereotype.Component;

/** 把 note 的附件接口与 OSS 读取适配成 NoteAttachmentPort。 */
@Component
public class FeignNoteAttachmentAdapter implements NoteAttachmentPort {

    private final NoteClient noteClient;
    private final StorageService storageService;

    public FeignNoteAttachmentAdapter(NoteClient noteClient, StorageService storageService) {
        this.noteClient = noteClient;
        this.storageService = storageService;
    }

    @Override
    public NoteAttachment requireOwned(Long noteId, Long userId) {
        return toAttachment(noteClient.requireOwnFile(noteId, userId));
    }

    @Override
    public NoteAttachment requireReadableOnline(Long noteId) {
        return toAttachment(noteClient.readableOnlineFile(noteId));
    }

    private NoteAttachment toAttachment(InternalNoteFile file) {
        return new NoteAttachment(file.contentType(), file.size(),
                () -> storageService.open(Bucket.NOTE, file.storageKey()));
    }
}
