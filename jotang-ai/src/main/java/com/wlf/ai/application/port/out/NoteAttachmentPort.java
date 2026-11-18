package com.wlf.ai.application.port.out;

/** 读取笔记附件的出站端口。归属与可见性由提供方判定。 */
public interface NoteAttachmentPort {

    /** 上传者本人可读；归属或状态不满足时抛业务异常。 */
    NoteAttachment requireOwned(Long noteId, Long userId);

    /** 任意在线笔记的附件。 */
    NoteAttachment requireReadableOnline(Long noteId);
}
