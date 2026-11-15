package com.wlf.ai.port;

import java.util.List;

/**
 * AI 读取笔记内容所需的跨服务端口。由提供方实现，AI 只依赖这个接口。
 */
public interface NoteContentReader {

    /** 上传者本人可读；归属或状态不满足时抛业务异常。 */
    NoteFileRef requireOwnFile(Long noteId, Long userId);

    /** 仅在线笔记，按标题精确匹配。 */
    List<NoteSearchHit> findOnlineByExactTitle(String title, int limit);

    /** 任意在线笔记的附件位置。 */
    NoteFileRef readableOnlineFile(Long noteId);
}
