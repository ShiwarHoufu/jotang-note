package com.wlf.ai.infrastructure.client.note;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * note-service 内部接口的 Feign 客户端。
 *
 * <p>服务名取 Nacos 注册名。过渡期 note 跑在 jotang-note 单体内，外提后名字不变。
 */
@FeignClient(name = "jotang-note",
        path = "/internal/notes",
        configuration = NoteClientConfig.class)
public interface NoteClient {

    @GetMapping("/{noteId}/file")
    InternalNoteFile requireOwnFile(@PathVariable("noteId") Long noteId,
                                    @RequestParam("userId") Long userId);

    @GetMapping("/search")
    List<InternalNoteSearchHit> search(@RequestParam("title") String title,
                                       @RequestParam("limit") int limit);

    @GetMapping("/{noteId}/readable-file")
    InternalNoteFile readableOnlineFile(@PathVariable("noteId") Long noteId);
}
