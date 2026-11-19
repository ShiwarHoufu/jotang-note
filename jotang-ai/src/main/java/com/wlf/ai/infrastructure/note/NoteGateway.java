package com.wlf.ai.infrastructure.note;

import com.wlf.storage.Bucket;
import com.wlf.storage.StorageService;
import org.springframework.stereotype.Component;

import java.util.List;

/** note-service 的检索、附件接口与 OSS 读取。 */
@Component
public class NoteGateway {

    private final NoteClient noteClient;
    private final StorageService storageService;

    public NoteGateway(NoteClient noteClient, StorageService storageService) {
        this.noteClient = noteClient;
        this.storageService = storageService;
    }

    /** 仅在线笔记，按标题精确匹配。 */
    public List<NoteHit> searchByTitle(String title, int limit) {
        return noteClient.search(title, limit).stream()
                .map(hit -> new NoteHit(hit.noteId(), hit.title(), hit.teacher(), hit.summary()))
                .toList();
    }

    /** 上传者本人可读；归属或状态不满足时抛业务异常。 */
    public NoteFile requireOwned(Long noteId, Long userId) {
        return toFile(noteClient.requireOwnFile(noteId, userId));
    }

    /** 任意在线笔记的附件。 */
    public NoteFile requireReadableOnline(Long noteId) {
        return toFile(noteClient.readableOnlineFile(noteId));
    }

    private NoteFile toFile(NoteClient.FileInfo file) {
        return new NoteFile(kindOf(file.contentType()), file.contentType(), file.size(),
                () -> storageService.open(Bucket.NOTE, file.storageKey()));
    }

    private static NoteFile.Kind kindOf(String contentType) {
        if (contentType == null) {
            return NoteFile.Kind.OTHER;
        }
        if (contentType.startsWith("image/")) {
            return NoteFile.Kind.IMAGE;
        }
        if ("text/plain".equals(contentType) || "text/markdown".equals(contentType)) {
            return NoteFile.Kind.TEXT;
        }
        return NoteFile.Kind.OTHER;
    }
}
