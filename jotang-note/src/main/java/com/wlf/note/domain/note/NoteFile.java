package com.wlf.note.domain.note;

import com.wlf.note.domain.note.model.ContentType;
import com.wlf.note.domain.note.model.FileSize;
import com.wlf.note.domain.note.model.OriginalFileName;
import com.wlf.note.domain.note.model.StorageKey;

/**
 * 笔记附件，属于 Note 聚合。
 *
 * <p>一份笔记一个文件，上传后不可更换。
 */
public final class NoteFile {

    private final StorageKey storageKey;
    private final OriginalFileName originalName;
    private final FileSize size;
    private final ContentType contentType;
    private boolean purged;

    private NoteFile(StorageKey storageKey,
                     OriginalFileName originalName,
                     FileSize size,
                     ContentType contentType,
                     boolean purged) {
        this.storageKey = storageKey;
        this.originalName = originalName;
        this.size = size;
        this.contentType = contentType;
        this.purged = purged;
    }

    public static NoteFile attach(StorageKey storageKey,
                                  OriginalFileName originalName,
                                  FileSize size,
                                  ContentType contentType) {
        return new NoteFile(storageKey, originalName, size, contentType, false);
    }

    public static NoteFile reconstitute(StorageKey storageKey,
                                        OriginalFileName originalName,
                                        FileSize size,
                                        ContentType contentType,
                                        boolean purged) {
        return new NoteFile(storageKey, originalName, size, contentType, purged);
    }

    /** 对象已物理清理，幂等。 */
    public void markPurged() {
        this.purged = true;
    }

    public StorageKey storageKey() {
        return storageKey;
    }

    public OriginalFileName originalName() {
        return originalName;
    }

    public FileSize size() {
        return size;
    }

    public ContentType contentType() {
        return contentType;
    }

    public boolean isPurged() {
        return purged;
    }
}
