package com.wlf.ai.application.port.out;

import java.util.List;

/** 按标题检索笔记的出站端口。 */
public interface NoteLookupPort {

    /** 仅在线笔记，按标题精确匹配。 */
    List<NoteSearchHit> findOnlineByExactTitle(String title, int limit);
}
